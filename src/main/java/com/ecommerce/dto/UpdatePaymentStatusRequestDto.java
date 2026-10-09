package com.ecommerce.dto;

import com.ecommerce.entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for updating an order's payment status (e.g. marking COD as collected/SUCCESS).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePaymentStatusRequestDto {

    @NotNull(message = "Payment status is required")
    private PaymentStatus paymentStatus;
}
