package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponseDto {

    private UUID id;
    private UUID productId;
    private String productName;
    private String productImageUrl;
    private BigDecimal productPrice;
    private int quantity;
    private BigDecimal lineTotal;
    private boolean productEnabled;
    private int productStock;
}
