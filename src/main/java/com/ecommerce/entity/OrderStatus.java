package com.ecommerce.entity;

/**
 * Lifecycle states for a customer Order.
 *
 * Flow:
 *   PENDING → CONFIRMED → SHIPPED → DELIVERED
 *                                 ↘
 *                              CANCELLED (from any state before DELIVERED)
 *
 * Inventory behaviour:
 *   Stock is reduced when order moves to CONFIRMED.
 *   Stock is restored when an order is CANCELLED (if stock was already reduced).
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
