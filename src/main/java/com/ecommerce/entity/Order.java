package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A customer order.
 *
 * Key design decisions:
 *  - "order" is a reserved SQL keyword — table name is "orders"
 *  - totalAmount uses BigDecimal — never floating-point for monetary values
 *  - address is a snapshot reference: the address chosen at checkout time.
 *    If the user later changes their address, historical orders are unaffected.
 *  - status progression: PENDING → CONFIRMED → SHIPPED → DELIVERED (or CANCELLED)
 *  - orderItems are owned by this Order (CascadeType.ALL)
 */
@Entity
@Table(name = "orders")  // "order" is a reserved SQL keyword
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"orderItems"}) // Exclude collection to prevent lazy-load in toString
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * The customer who placed this order.
     * LAZY fetch — user details loaded on demand.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_user"))
    private User user;

    /**
     * The delivery address selected at checkout time.
     * LAZY fetch — snapshot of the address used for this order.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "address_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_address"))
    private Address address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.CONFIRMED;

    /**
     * Total monetary value of this order.
     * BigDecimal with precision=12, scale=2 supports values up to 9,999,999,999.99.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Line items belonging to this order.
     * CascadeType.ALL — order items are created/deleted with the order.
     * Not using orphanRemoval because order items are immutable after creation.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> orderItems = new ArrayList<>();
}
