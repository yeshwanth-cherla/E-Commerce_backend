package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

/**
 * Delivery address belonging to a User.
 *
 * A User can have multiple addresses.
 * One address can be marked as the default delivery address.
 */
@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "user") // Exclude to prevent recursive toString with User
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * The user who owns this address.
     * LAZY fetch — address is loaded on demand, not eagerly with the user.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_address_user"))
    private User user;

    @Column(nullable = false, length = 255)
    private String street;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 20)
    private String zip;

    @Column(nullable = false, length = 100)
    private String country;

    /**
     * Whether this is the user's default shipping address.
     * Only one address per user should have isDefault = true.
     * Enforced in the service layer.
     */
    @Column(nullable = false)
    private boolean isDefault = false;
}
