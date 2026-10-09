package com.ecommerce.controller;

import com.ecommerce.dto.InventoryAdjustmentRequestDto;
import com.ecommerce.dto.InventoryAdjustmentResponseDto;
import com.ecommerce.dto.InventoryItemResponseDto;
import com.ecommerce.dto.PaginatedResponseDto;
import com.ecommerce.service.InventoryService;
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
 * REST Controller for Admin Inventory Management.
 *
 * All endpoints require authentication and ROLE_ADMIN authorization.
 */
@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * View inventory with optional search, category, and enabled filters.
     */
    @GetMapping
    public ResponseEntity<PaginatedResponseDto<InventoryItemResponseDto>> getInventory(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "false") boolean enabledOnly,
            @RequestParam(defaultValue = "10") int threshold,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int safeSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, safeSize);
        Page<InventoryItemResponseDto> result = inventoryService.getInventory(
                search, categoryId, enabledOnly, threshold, pageable);

        return ResponseEntity.ok(PaginatedResponseDto.fromPage(result));
    }

    /**
     * View products with stock at or below the specified threshold.
     */
    @GetMapping("/low-stock")
    public ResponseEntity<PaginatedResponseDto<InventoryItemResponseDto>> getLowStockProducts(
            @RequestParam(defaultValue = "10") int threshold,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int safeSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, safeSize);
        Page<InventoryItemResponseDto> result = inventoryService.getLowStockProducts(
                threshold, search, categoryId, pageable);

        return ResponseEntity.ok(PaginatedResponseDto.fromPage(result));
    }

    /**
     * Manually adjust a product's stock quantity (SET, ADD, SUBTRACT) with audit reason logging.
     */
    @PostMapping("/adjust")
    public ResponseEntity<InventoryAdjustmentResponseDto> adjustStock(
            @Valid @RequestBody InventoryAdjustmentRequestDto dto) {

        InventoryAdjustmentResponseDto response = inventoryService.adjustStock(dto);
        return ResponseEntity.ok(response);
    }

    /**
     * View audit history of manual stock adjustments, optionally filtered by product ID.
     */
    @GetMapping("/adjustments")
    public ResponseEntity<PaginatedResponseDto<InventoryAdjustmentResponseDto>> getAdjustmentHistory(
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int safeSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, safeSize);
        Page<InventoryAdjustmentResponseDto> history = inventoryService.getAdjustmentHistory(productId, pageable);

        return ResponseEntity.ok(PaginatedResponseDto.fromPage(history));
    }
}
