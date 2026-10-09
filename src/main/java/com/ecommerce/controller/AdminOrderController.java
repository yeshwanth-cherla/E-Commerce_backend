package com.ecommerce.controller;

import com.ecommerce.dto.OrderResponseDto;
import com.ecommerce.dto.PaginatedResponseDto;
import com.ecommerce.dto.UpdateOrderStatusRequestDto;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.service.AdminOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for Administrator Order Management.
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    /**
     * Search and filter all orders with pagination.
     */
    @GetMapping
    public ResponseEntity<PaginatedResponseDto<OrderResponseDto>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int safeSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, safeSize);
        Page<OrderResponseDto> orders = adminOrderService.getAllOrders(status, userId, pageable);

        return ResponseEntity.ok(PaginatedResponseDto.fromPage(orders));
    }

    /**
     * View details of any order.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponseDto> getOrderById(@PathVariable UUID orderId) {
        OrderResponseDto order = adminOrderService.getOrderById(orderId);
        return ResponseEntity.ok(order);
    }

    /**
     * Update order status following state transition rules (triggers stock restoration on cancellation).
     */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponseDto> updateOrderStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequestDto dto) {

        OrderResponseDto updatedOrder = adminOrderService.updateOrderStatus(orderId, dto);
        return ResponseEntity.ok(updatedOrder);
    }
}
