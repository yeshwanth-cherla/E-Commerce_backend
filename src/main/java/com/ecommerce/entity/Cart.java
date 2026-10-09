package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shopping cart — one per user.
 *
 * Relationship:
 *   User 1 ──── 1 Cart (enforced by unique constraint on user_id)
 *   Cart 1 ──── N CartItems
 *
 * Cart items are fully owned by the Cart:
 *   - CascadeType.ALL: persist/merge/remove cascade to items
 *   - orphanRemoval: removing an item from the list deletes it from DB
 */
@Entity
@Table(
        name = "carts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_carts_user_id", columnNames = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "cartItems") // Exclude to prevent loading collection in toString
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * The owner of this cart.
     * LAZY fetch — we don't always need full user details when working with a cart.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_cart_user"))
    private User user;

    /**
     * Items currently in this cart.
     * orphanRemoval ensures that when an item is removed from this list,
     * it is also deleted from the database.
     */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<CartItem> cartItems = new ArrayList<>();
}
