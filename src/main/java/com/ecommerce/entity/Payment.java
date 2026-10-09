package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Payment record associated with an Order.
 *
 * Design decisions:
 *  - One Payment per Order (1:1 relationship)
 *  - Payment is owned by the Order (FK on payments table)
 *  - transactionReference is populated by the payment gateway
 *    (mock value in Stage 08; real gateway reference in future)
 *  - paidAt is null until payment is successfully processed
 *  - No sensitive card data is stored (PCI compliance)
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "order") // Exclude to prevent recursive toString
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * The order this payment is for.
     * LAZY fetch — payment details are not always needed with order.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_payment_order"))
    private Order order;

    /**
     * Payment method identifier (e.g., "CARD", "UPI", "MOCK").
     * Will be driven by the payment gateway integration in a later stage.
     */
    @Column(length = 50)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    /**
     * External payment gateway transaction reference.
     * Null until a payment attempt is made.
     * Do NOT store card numbers, CVV, or other sensitive payment data here.
     */
    @Column(length = 255)
    private String transactionReference;

    /**
     * Timestamp of successful payment.
     * Null if payment is still PENDING or has FAILED.
     */
    @Column
    private Instant paidAt;
}
