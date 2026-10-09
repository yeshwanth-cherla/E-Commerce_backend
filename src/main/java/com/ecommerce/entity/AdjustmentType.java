package com.ecommerce.entity;

/**
 * Type of manual stock adjustment performed by an administrator.
 */
public enum AdjustmentType {
    /** Override stock quantity directly to a specified target value. */
    SET,
    /** Increment stock quantity by a specified amount. */
    ADD,
    /** Decrement stock quantity by a specified amount. */
    SUBTRACT
}
