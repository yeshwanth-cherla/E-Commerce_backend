package com.ecommerce.dto;

import com.ecommerce.entity.AdjustmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for an audit record of a stock adjustment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustmentResponseDto {

    private UUID id;
    private UUID productId;
    private String productName;
    private int previousStock;
    private int newStock;
    private int quantityDelta;
    private AdjustmentType adjustmentType;
    private String reason;
    private UUID performedByUserId;
    private Instant createdAt;
}
