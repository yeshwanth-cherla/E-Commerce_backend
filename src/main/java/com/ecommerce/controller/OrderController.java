package com.ecommerce.controller;

import com.ecommerce.dto.CheckoutRequestDto;
import com.ecommerce.dto.OrderResponseDto;
import com.ecommerce.dto.PaginatedResponseDto;
import com.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for the Order & Checkout module.
 *
 * All endpoints require authentication (Spring Security enforces this globally).
 * User identity is ALWAYS derived from the JWT — never from a client-supplied field.
 *
 * Endpoints:
 *   POST   /api/orders/checkout           — create a new order from the current cart
 *   GET    /api/orders                    — paginated order history for the current user
 *   GET    /api/orders/{orderId}          — single order detail (owner-only)
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Converts the authenticated user's current cart into a new Order.
     *
     * Returns 201 CREATED with the order body on success.
     * Returns 400 if the cart is empty or validation fails.
     * Returns 404 if the address is not found or doesn't belong to the user.
     * Returns 409 if a product is disabled or has insufficient stock.
     */
    @PostMapping("/checkout")
    public ResponseEntity<OrderResponseDto> checkout(
            @Valid @RequestBody CheckoutRequestDto dto) {
        OrderResponseDto order = orderService.checkout(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * Returns the authenticated user's orders, newest first, paginated.
     *
     * Query params:
     *   page (default 0) — zero-based page index
     *   size (default 10) — items per page, max 50
     */
    @GetMapping
    public ResponseEntity<PaginatedResponseDto<OrderResponseDto>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int safeSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, safeSize);
        Page<OrderResponseDto> orders = orderService.getOrdersForCurrentUser(pageable);

        return ResponseEntity.ok(PaginatedResponseDto.fromPage(orders));
    }

    /**
     * Returns a single order by ID.
     *
     * Returns 200 with the order body if the order belongs to the current user.
     * Returns 404 if the order doesn't exist OR belongs to a different user
     * (intentional: avoids leaking the existence of other users' orders).
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponseDto> getOrderById(@PathVariable UUID orderId) {
        OrderResponseDto order = orderService.getOrderById(orderId);
        return ResponseEntity.ok(order);
    }
}
