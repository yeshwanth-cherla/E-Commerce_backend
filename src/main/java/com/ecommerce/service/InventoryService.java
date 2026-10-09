package com.ecommerce.service;

import com.ecommerce.dto.*;
import com.ecommerce.entity.AdjustmentType;
import com.ecommerce.entity.InventoryAdjustment;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.ResourceConflictException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.InventoryAdjustmentRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles inventory management operations: visibility, stock adjustments, low-stock monitoring, and audit logging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;

    /**
     * Returns a paginated list of inventory items with filtering options.
     */
    @Transactional(readOnly = true)
    public Page<InventoryItemResponseDto> getInventory(
            String search, Long categoryId, boolean enabledOnly, int threshold, Pageable pageable) {

        Page<Product> products = productRepository.findProductsWithFilters(search, categoryId, enabledOnly, pageable);
        return products.map(product -> mapToInventoryItemResponseDto(product, threshold));
    }

    /**
     * Returns products whose stock quantity is at or below the specified threshold.
     */
    @Transactional(readOnly = true)
    public Page<InventoryItemResponseDto> getLowStockProducts(
            int threshold, String search, Long categoryId, Pageable pageable) {

        if (threshold < 0) {
            throw new IllegalArgumentException("Low stock threshold cannot be negative: " + threshold);
        }

        Page<Product> products = productRepository.findLowStockProducts(threshold, search, categoryId, pageable);
        return products.map(product -> mapToInventoryItemResponseDto(product, threshold));
    }

    /**
     * Atomically adjusts a product's stock quantity under a pessimistic write lock and records an audit log.
     *
     * @param dto the adjustment details
     * @return the created audit adjustment log
     */
    @Transactional
    public InventoryAdjustmentResponseDto adjustStock(InventoryAdjustmentRequestDto dto) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));

        // Acquire pessimistic FOR UPDATE lock on target product
        Product product = productRepository.findByIdWithPessimisticLock(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id : '" + dto.getProductId() + "'"));

        int previousStock = product.getStockQuantity();
        int newStock;

        if (dto.getType() == AdjustmentType.SET) {
            newStock = dto.getAmount();
        } else if (dto.getType() == AdjustmentType.ADD) {
            newStock = previousStock + dto.getAmount();
        } else if (dto.getType() == AdjustmentType.SUBTRACT) {
            newStock = previousStock - dto.getAmount();
        } else {
            throw new IllegalArgumentException("Unsupported adjustment type: " + dto.getType());
        }

        if (newStock < 0) {
            throw new ResourceConflictException(
                    "Insufficient stock for adjustment — current stock is " + previousStock
                    + ", requested reduction makes stock negative (" + newStock + ")");
        }

        int quantityDelta = newStock - previousStock;
        product.setStockQuantity(newStock);
        productRepository.save(product);

        InventoryAdjustment logEntry = new InventoryAdjustment();
        logEntry.setProduct(product);
        logEntry.setPreviousStock(previousStock);
        logEntry.setNewStock(newStock);
        logEntry.setQuantityDelta(quantityDelta);
        logEntry.setAdjustmentType(dto.getType());
        logEntry.setReason(dto.getReason());
        logEntry.setPerformedByUserId(currentUserId);

        InventoryAdjustment savedLog = inventoryAdjustmentRepository.save(logEntry);

        log.info("Inventory adjusted: productId={}, type={}, prevStock={}, newStock={}, reason='{}', adminId={}",
                product.getId(), dto.getType(), previousStock, newStock, dto.getReason(), currentUserId);

        return mapToAdjustmentResponseDto(savedLog);
    }

    /**
     * Returns paginated stock adjustment audit logs, optionally filtered by product ID.
     */
    @Transactional(readOnly = true)
    public Page<InventoryAdjustmentResponseDto> getAdjustmentHistory(UUID productId, Pageable pageable) {
        if (productId != null) {
            if (!productRepository.existsById(productId)) {
                throw new ResourceNotFoundException("Product not found with id : '" + productId + "'");
            }
            return inventoryAdjustmentRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)
                    .map(this::mapToAdjustmentResponseDto);
        }
        return inventoryAdjustmentRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::mapToAdjustmentResponseDto);
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                            //
    // ------------------------------------------------------------------ //

    private InventoryItemResponseDto mapToInventoryItemResponseDto(Product product, int threshold) {
        CategoryResponseDto categoryDto = null;
        if (product.getCategory() != null) {
            categoryDto = CategoryResponseDto.builder()
                    .id(product.getCategory().getId())
                    .name(product.getCategory().getName())
                    .description(product.getCategory().getDescription())
                    .build();
        }

        return InventoryItemResponseDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .lowStock(product.getStockQuantity() <= threshold)
                .enabled(product.isEnabled())
                .category(categoryDto)
                .createdAt(product.getCreatedAt())
                .build();
    }

    private InventoryAdjustmentResponseDto mapToAdjustmentResponseDto(InventoryAdjustment log) {
        return InventoryAdjustmentResponseDto.builder()
                .id(log.getId())
                .productId(log.getProduct().getId())
                .productName(log.getProduct().getName())
                .previousStock(log.getPreviousStock())
                .newStock(log.getNewStock())
                .quantityDelta(log.getQuantityDelta())
                .adjustmentType(log.getAdjustmentType())
                .reason(log.getReason())
                .performedByUserId(log.getPerformedByUserId())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
