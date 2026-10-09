package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single line item within an Order.
 *
 * CRITICAL design decision — priceAtPurchase:
 *   The product price at the time the order was placed is captured here.
 *   This ensures historical order accuracy even if the product price changes later.
 *   NEVER calculate historical order value from Product.price.
 *
 * The product reference is kept for display purposes (name, image, etc.)
 * but the authoritative price is priceAtPurchase.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"order", "product"}) // Exclude to prevent recursive toString
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * The parent order.
     * LAZY fetch — OrderItems are usually accessed via Order.orderItems.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_item_order"))
    private Order order;

    /**
     * Reference to the product.
     * LAZY fetch — loaded on demand for product details display.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_item_product"))
    private Product product;

    @Column(nullable = false)
    private int quantity;

    /**
     * Price of a single unit of this product AT THE TIME OF ORDER PLACEMENT.
     * This is a snapshot — immune to future product price changes.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtPurchase;
}
