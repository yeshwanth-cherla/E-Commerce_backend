package com.ecommerce.controller;

import com.ecommerce.dto.OrderResponseDto;
import com.ecommerce.dto.PaginatedResponseDto;
import com.ecommerce.dto.UpdateUserStatusRequestDto;
import com.ecommerce.dto.UserResponseDto;
import com.ecommerce.service.AdminUserService;
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
 * REST Controller for Administrator User Management.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * Search and list users with pagination.
     */
    @GetMapping
    public ResponseEntity<PaginatedResponseDto<UserResponseDto>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int safeSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, safeSize);
        Page<UserResponseDto> users = adminUserService.getUsers(search, pageable);

        return ResponseEntity.ok(PaginatedResponseDto.fromPage(users));
    }

    /**
     * View a single user profile.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable UUID userId) {
        UserResponseDto user = adminUserService.getUserById(userId);
        return ResponseEntity.ok(user);
    }

    /**
     * Toggle a user's enabled/disabled status.
     */
    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserResponseDto> updateUserStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequestDto dto) {

        UserResponseDto updatedUser = adminUserService.updateUserStatus(userId, dto);
        return ResponseEntity.ok(updatedUser);
    }

    /**
     * View a specific user's order history.
     */
    @GetMapping("/{userId}/orders")
    public ResponseEntity<PaginatedResponseDto<OrderResponseDto>> getUserOrders(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int safeSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, safeSize);
        Page<OrderResponseDto> orders = adminUserService.getUserOrders(userId, pageable);

        return ResponseEntity.ok(PaginatedResponseDto.fromPage(orders));
    }
}
