package com.ecommerce.dto;

import com.ecommerce.entity.AdjustmentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request payload for stock adjustments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustmentRequestDto {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Adjustment type is required")
    private AdjustmentType type;

    @NotNull(message = "Amount is required")
    @Min(value = 0, message = "Amount must be non-negative")
    private Integer amount;

    @NotBlank(message = "Reason for adjustment is required")
    private String reason;
}
