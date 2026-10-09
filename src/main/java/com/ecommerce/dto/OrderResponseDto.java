package com.ecommerce.dto;

import com.ecommerce.entity.OrderStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable response DTO for a customer Order.
 *
 * totalAmount is the authoritative order total captured at checkout.
 * It is NEVER recalculated from current product prices.
 */
@Value
@Builder
public class OrderResponseDto {

    UUID id;

    OrderStatus status;

    /** Total monetary value captured at checkout — immutable. */
    BigDecimal totalAmount;

    /** Snapshot of the delivery address used at checkout. */
    AddressSnapshotDto address;

    List<OrderItemResponseDto> items;

    Instant createdAt;

    /**
     * Lightweight address snapshot embedded inside an order response.
     * Avoids exposing the full Address entity and isolates the historical snapshot.
     */
    @Value
    @Builder
    public static class AddressSnapshotDto {
        UUID id;
        String street;
        String city;
        String state;
        String zip;
        String country;
    }
}
