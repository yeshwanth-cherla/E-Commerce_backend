package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Product in the catalogue.
 *
 * Key design decisions:
 *  - price uses BigDecimal (precision=10, scale=2) — never floating-point for money
 *  - averageRating is stored as a denormalized field and updated when reviews are added
 *  - stockQuantity is managed by the Inventory module (Stage 09)
 *  - enabled flag allows soft-disabling without deleting the product
 *  - imageUrl is a simple string — Supabase Storage integration is a later enhancement
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "category") // Avoid recursive toString
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Product price.
     * BigDecimal with precision=10, scale=2 supports values up to 99,999,999.99.
     * NEVER use float or double for monetary values.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stockQuantity = 0;

    @Column(length = 2048)
    private String imageUrl;

    /**
     * Category this product belongs to.
     * LAZY fetch — category details are not always needed when loading a product list.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_category"))
    private Category category;

    /**
     * Denormalized average rating (0.0 to 5.0).
     * Recalculated whenever a review is added/updated/deleted.
     * Nullable — null means no reviews yet.
     */
    @Column(precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
