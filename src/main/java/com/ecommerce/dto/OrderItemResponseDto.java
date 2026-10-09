package com.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Immutable response DTO for a single line item within an order.
 *
 * priceAtPurchase is the authoritative price — never recalculated from
 * the current Product.price, which may have changed since the order was placed.
 */
@Value
@Builder
public class OrderItemResponseDto {

    UUID id;

    UUID productId;

    /** Product name at time of response (display purposes only). */
    String productName;

    /** Product image URL (display purposes only). */
    String productImageUrl;

    /** Unit price captured at order placement — immutable historical record. */
    BigDecimal priceAtPurchase;

    int quantity;

    /** Computed: priceAtPurchase × quantity */
    BigDecimal lineTotal;
}
