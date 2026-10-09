package com.ecommerce.entity;

/**
 * Payment transaction states.
 *
 * Flow (mock payment in Stage 08):
 *   PENDING → SUCCESS
 *          → FAILED
 *
 * REFUNDED is set if an order is cancelled after successful payment.
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}
