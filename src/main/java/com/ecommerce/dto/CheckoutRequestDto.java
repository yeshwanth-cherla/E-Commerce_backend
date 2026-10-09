package com.ecommerce.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

/**
 * Request body for the checkout endpoint.
 *
 * The client supplies only the address UUID for delivery.
 * The authenticated user's identity is always derived from the JWT —
 * NEVER from a client-supplied field.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequestDto {

    /**
     * ID of the delivery address to use for this order.
     * Must belong to the authenticated user — validated in OrderService.
     */
    @NotNull(message = "Delivery address ID is required")
    private UUID addressId;
}
