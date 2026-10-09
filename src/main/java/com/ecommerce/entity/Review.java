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
 * Product review submitted by a customer.
 *
 * Business rules:
 *  - A user can submit at most ONE review per product.
 *    Enforced by a database-level unique constraint on (user_id, product_id).
 *  - Rating is an integer 1–5 (enforced in service layer via validation in Stage 10).
 *  - Product.averageRating is recalculated whenever a review is added/updated/deleted.
 */
@Entity
@Table(
        name = "reviews",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reviews_user_product",
                        columnNames = {"user_id", "product_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"user", "product"}) // Exclude to prevent recursive toString
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * The user who submitted this review.
     * LAZY fetch — user details loaded on demand.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_review_user"))
    private User user;

    /**
     * The product being reviewed.
     * LAZY fetch — product details loaded on demand.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_review_product"))
    private Product product;

    /**
     * Star rating from 1 to 5.
     * Min/Max validation is enforced by @Valid in the request DTO (Stage 10).
     */
    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
