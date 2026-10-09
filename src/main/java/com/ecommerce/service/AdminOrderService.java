package com.ecommerce.service;

import com.ecommerce.dto.OrderItemResponseDto;
import com.ecommerce.dto.OrderResponseDto;
import com.ecommerce.dto.OrderResponseDto.AddressSnapshotDto;
import com.ecommerce.dto.UpdateOrderStatusRequestDto;
import com.ecommerce.dto.UpdatePaymentStatusRequestDto;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.PaymentStatus;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.ResourceConflictException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Handles administrative order management: listing, inspection, status progression, stock restoration, and payment collection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Lists orders with status and user filtering, paginated.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getAllOrders(OrderStatus status, UUID userId, Pageable pageable) {
        return orderRepository.findOrdersWithFilters(status, userId, pageable)
                .map(this::mapToOrderResponseDto);
    }

    /**
     * Retrieves any order by ID.
     */
    @Transactional(readOnly = true)
    public OrderResponseDto getOrderById(UUID orderId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id : '" + orderId + "'"));
        return mapToOrderResponseDto(order);
    }

    /**
     * Updates an order's status enforcing explicit state transition rules.
     * Restores product stock quantities under pessimistic locks if the order is cancelled.
     */
    @Transactional
    public OrderResponseDto updateOrderStatus(UUID orderId, UpdateOrderStatusRequestDto dto) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id : '" + orderId + "'"));

        OrderStatus current = order.getStatus();
        OrderStatus target = dto.getStatus();

        if (current == target) {
            return mapToOrderResponseDto(order);
        }

        validateStatusTransition(current, target);

        // If transitioning to CANCELLED, restore product stock under pessimistic write locks
        if (target == OrderStatus.CANCELLED) {
            restoreStockForCancelledOrder(order);
        }

        order.setStatus(target);
        Order savedOrder = orderRepository.save(order);

        log.info("Order status updated by admin: orderId={}, previousStatus={}, newStatus={}",
                orderId, current, target);

        return mapToOrderResponseDto(savedOrder);
    }

    /**
     * Updates payment status for an order (e.g., marking COD payment as collected).
     * Prevents collecting payment for a cancelled order.
     */
    @Transactional
    public OrderResponseDto updatePaymentStatus(UUID orderId, UpdatePaymentStatusRequestDto dto) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id : '" + orderId + "'"));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResourceConflictException("Cannot update payment status for a cancelled order");
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseGet(() -> {
                    Payment p = new Payment();
                    p.setOrder(order);
                    p.setMethod("COD");
                    p.setStatus(PaymentStatus.PENDING);
                    return p;
                });

        payment.setStatus(dto.getPaymentStatus());
        if (dto.getPaymentStatus() == PaymentStatus.SUCCESS && payment.getPaidAt() == null) {
            payment.setPaidAt(Instant.now());
        }

        paymentRepository.save(payment);

        log.info("Payment status updated by admin: orderId={}, newPaymentStatus={}",
                orderId, dto.getPaymentStatus());

        return mapToOrderResponseDto(order);
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                            //
    // ------------------------------------------------------------------ //

    private void validateStatusTransition(OrderStatus current, OrderStatus target) {
        if (current == OrderStatus.DELIVERED) {
            throw new ResourceConflictException("Cannot change status of an order that has already been delivered");
        }
        if (current == OrderStatus.CANCELLED) {
            throw new ResourceConflictException("Cannot change status of an order that has already been cancelled");
        }

        boolean valid = false;
        if (current == OrderStatus.PENDING && (target == OrderStatus.CONFIRMED || target == OrderStatus.CANCELLED)) {
            valid = true;
        } else if (current == OrderStatus.CONFIRMED && (target == OrderStatus.SHIPPED || target == OrderStatus.CANCELLED)) {
            valid = true;
        } else if (current == OrderStatus.SHIPPED && (target == OrderStatus.DELIVERED || target == OrderStatus.CANCELLED)) {
            valid = true;
        }

        if (!valid) {
            throw new ResourceConflictException(
                    "Invalid order status transition from " + current + " to " + target);
        }
    }

    private void restoreStockForCancelledOrder(Order order) {
        List<UUID> productIds = order.getOrderItems().stream()
                .map(item -> item.getProduct().getId())
                .distinct()
                .collect(Collectors.toList());

        if (productIds.isEmpty()) {
            return;
        }

        // Acquire pessimistic write locks on all involved products
        List<Product> lockedProducts = productRepository.findAllByIdWithPessimisticLock(productIds);
        Map<UUID, Product> productMap = lockedProducts.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (OrderItem item : order.getOrderItems()) {
            Product product = productMap.get(item.getProduct().getId());
            if (product != null) {
                int oldStock = product.getStockQuantity();
                product.setStockQuantity(oldStock + item.getQuantity());
                productRepository.save(product);
                log.info("Restored stock for product {}: prev={}, restored={}, new={}",
                        product.getId(), oldStock, item.getQuantity(), product.getStockQuantity());
            }
        }
    }

    private OrderResponseDto mapToOrderResponseDto(Order order) {
        List<OrderItemResponseDto> itemDtos = order.getOrderItems().stream()
                .map(this::mapToOrderItemResponseDto)
                .collect(Collectors.toList());

        AddressSnapshotDto addressDto = AddressSnapshotDto.builder()
                .id(order.getAddress().getId())
                .street(order.getAddress().getStreet())
                .city(order.getAddress().getCity())
                .state(order.getAddress().getState())
                .zip(order.getAddress().getZip())
                .country(order.getAddress().getCountry())
                .build();

        String paymentMethod = "COD";
        PaymentStatus paymentStatus = PaymentStatus.PENDING;
        Instant paidAt = null;

        Optional<Payment> paymentOpt = paymentRepository.findByOrderId(order.getId());
        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            if (payment.getMethod() != null) {
                paymentMethod = payment.getMethod();
            }
            if (payment.getStatus() != null) {
                paymentStatus = payment.getStatus();
            }
            paidAt = payment.getPaidAt();
        }

        return OrderResponseDto.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .address(addressDto)
                .items(itemDtos)
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentStatus)
                .paidAt(paidAt)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderItemResponseDto mapToOrderItemResponseDto(OrderItem item) {
        Product product = item.getProduct();
        BigDecimal lineTotal = item.getPriceAtPurchase()
                .multiply(BigDecimal.valueOf(item.getQuantity()));

        return OrderItemResponseDto.builder()
                .id(item.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productImageUrl(product.getImageUrl())
                .priceAtPurchase(item.getPriceAtPurchase())
                .quantity(item.getQuantity())
                .lineTotal(lineTotal)
                .build();
    }
}
