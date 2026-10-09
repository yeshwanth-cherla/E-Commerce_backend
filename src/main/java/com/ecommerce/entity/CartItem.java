package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

/**
 * An individual line item within a Cart.
 *
 * Constraints:
 *  - quantity must be at least 1 (enforced in service layer in Stage 07)
 *  - a product can appear only once per cart
 *    (enforced via unique constraint on cart_id + product_id)
 */
@Entity
@Table(
        name = "cart_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cart_items_cart_product",
                        columnNames = {"cart_id", "product_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"cart", "product"}) // Exclude to prevent recursive toString
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * The cart this item belongs to.
     * LAZY fetch — always accessed via Cart.cartItems, not loaded independently.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cart_item_cart"))
    private Cart cart;

    /**
     * The product this line item refers to.
     * LAZY fetch — product details loaded on demand.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cart_item_product"))
    private Product product;

    /**
     * Requested quantity. Must be >= 1.
     * Must not exceed product.stockQuantity — enforced in CartService (Stage 07).
     */
    @Column(nullable = false)
    private int quantity;
}
