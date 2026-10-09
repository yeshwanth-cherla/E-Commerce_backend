package com.ecommerce.exception;

/**
 * Custom runtime exception thrown when a resource creation/update conflicts
 * with existing data (e.g. duplicate category name, or deleting a referenced category).
 */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
