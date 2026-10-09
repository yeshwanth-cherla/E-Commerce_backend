package com.ecommerce.service;

import com.ecommerce.dto.CheckoutRequestDto;
import com.ecommerce.dto.OrderItemResponseDto;
import com.ecommerce.dto.OrderResponseDto;
import com.ecommerce.dto.OrderResponseDto.AddressSnapshotDto;
import com.ecommerce.entity.*;
import com.ecommerce.exception.ResourceConflictException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.*;
import com.ecommerce.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Handles the order lifecycle: checkout, order history, and order detail retrieval.
 *
 * Checkout correctness:
 *  1. Load the user's cart (with items + products).
 *  2. Acquire PESSIMISTIC_WRITE locks on all involved products — prevents overselling
 *     under concurrent checkouts.
 *  3. Validate each cart item: product enabled, sufficient stock.
 *  4. Snapshot each product's current price into OrderItem.priceAtPurchase.
 *  5. Decrement stockQuantity on every product.
 *  6. Persist the Order and its OrderItems (cascade).
 *  7. Clear all cart items.
 *
 * The entire operation runs inside a single @Transactional boundary.
 * Any failure (stock conflict, disabled product, DB error) causes a full rollback,
 * leaving cart, stock, and orders in their original state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    // ------------------------------------------------------------------ //
    //  Checkout                                                            //
    // ------------------------------------------------------------------ //

    /**
     * Atomically converts the current user's cart into a confirmed Order.
     *
     * @param dto contains only the delivery address UUID
     * @return the persisted order as a response DTO
     */
    @Transactional
    public OrderResponseDto checkout(CheckoutRequestDto dto) {
        UUID currentUserId = requireCurrentUserId();

        // --- 1. Load user ---
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id : '" + currentUserId + "'"));

        // --- 2. Load cart with items ---
        Cart cart = cartRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new ResourceConflictException(
                        "Cart is empty — add items before checking out"));

        List<CartItem> cartItems = cart.getCartItems();
        if (cartItems == null || cartItems.isEmpty()) {
            throw new ResourceConflictException(
                    "Cart is empty — add items before checking out");
        }

        // --- 3. Verify address belongs to this user ---
        Address address = addressRepository.findById(dto.getAddressId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address not found with id : '" + dto.getAddressId() + "'"));

        if (!address.getUser().getId().equals(currentUserId)) {
            throw new ResourceNotFoundException(
                    "Address not found with id : '" + dto.getAddressId() + "'");
        }

        // --- 4. Collect product IDs and acquire pessimistic locks ---
        List<UUID> productIds = cartItems.stream()
                .map(ci -> ci.getProduct().getId())
                .distinct()
                .collect(Collectors.toList());

        List<Product> lockedProducts =
                productRepository.findAllByIdWithPessimisticLock(productIds);

        // Build a fast look-up map by product ID
        Map<UUID, Product> productMap = lockedProducts.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // --- 5. Validate stock and compute total ---
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>(cartItems.size());

        for (CartItem ci : cartItems) {
            UUID productId = ci.getProduct().getId();
            Product product = productMap.get(productId);

            if (product == null) {
                throw new ResourceNotFoundException(
                        "Product not found with id : '" + productId + "'");
            }
            if (!product.isEnabled()) {
                throw new ResourceConflictException(
                        "Product '" + product.getName() + "' is no longer available");
            }
            if (ci.getQuantity() > product.getStockQuantity()) {
                throw new ResourceConflictException(
                        "Insufficient stock for '" + product.getName() + "' — requested "
                        + ci.getQuantity() + ", available " + product.getStockQuantity());
            }

            // Snapshot price at purchase time
            BigDecimal unitPrice = product.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(ci.getQuantity()));
            totalAmount = totalAmount.add(lineTotal);

            // --- 6. Decrement stock ---
            product.setStockQuantity(product.getStockQuantity() - ci.getQuantity());

            OrderItem oi = new OrderItem();
            oi.setProduct(product);
            oi.setQuantity(ci.getQuantity());
            oi.setPriceAtPurchase(unitPrice);
            orderItems.add(oi);
        }

        // --- 7. Create order ---
        Order order = new Order();
        order.setUser(user);
        order.setAddress(address);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(totalAmount);

        // Wire order items to the order (cascade will persist them)
        for (OrderItem oi : orderItems) {
            oi.setOrder(order);
        }
        order.getOrderItems().addAll(orderItems);

        Order savedOrder = orderRepository.save(order);

        // --- 8. Clear cart ---
        cart.getCartItems().clear();
        cartRepository.save(cart);

        log.info("Checkout complete: orderId={}, userId={}, total={}, items={}",
                savedOrder.getId(), currentUserId, totalAmount, orderItems.size());

        return mapToOrderResponseDto(savedOrder);
    }

    // ------------------------------------------------------------------ //
    //  Order history                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Returns the authenticated user's orders, newest first, paginated.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getOrdersForCurrentUser(Pageable pageable) {
        UUID currentUserId = requireCurrentUserId();

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id : '" + currentUserId + "'"));

        Page<Order> orders = orderRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return orders.map(this::mapToOrderResponseDto);
    }

    // ------------------------------------------------------------------ //
    //  Order detail                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Returns a single order. Enforces ownership: returns 404 if the order
     * belongs to a different user, to prevent enumeration attacks.
     */
    @Transactional(readOnly = true)
    public OrderResponseDto getOrderById(UUID orderId) {
        UUID currentUserId = requireCurrentUserId();

        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id : '" + orderId + "'"));

        // Strict ownership — 404 (not 403) to avoid leaking existence
        if (!order.getUser().getId().equals(currentUserId)) {
            throw new ResourceNotFoundException(
                    "Order not found with id : '" + orderId + "'");
        }

        return mapToOrderResponseDto(order);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private UUID requireCurrentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
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

        return OrderResponseDto.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .address(addressDto)
                .items(itemDtos)
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
