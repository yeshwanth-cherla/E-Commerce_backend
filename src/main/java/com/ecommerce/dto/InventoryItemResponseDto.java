package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a product's inventory status for administrative monitoring.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemResponseDto {

    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private int stockQuantity;
    private boolean lowStock;
    private boolean enabled;
    private CategoryResponseDto category;
    private Instant createdAt;
}
