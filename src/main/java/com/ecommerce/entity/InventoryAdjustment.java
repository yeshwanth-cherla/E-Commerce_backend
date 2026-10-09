package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit trail record for manual inventory adjustments made by administrators.
 */
@Entity
@Table(name = "inventory_adjustments")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "product")
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_adjustment_product"))
    private Product product;

    @Column(nullable = false)
    private int previousStock;

    @Column(nullable = false)
    private int newStock;

    @Column(nullable = false)
    private int quantityDelta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdjustmentType adjustmentType;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(columnDefinition = "uuid", nullable = false)
    private UUID performedByUserId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
