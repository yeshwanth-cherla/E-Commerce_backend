package com.ecommerce.repository;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * All orders for a user, newest first, with items + products eager-loaded.
     * The EntityGraph avoids N+1 queries when iterating over order items.
     */
    @EntityGraph(attributePaths = {"orderItems", "orderItems.product", "address", "user"})
    Page<Order> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    List<Order> findByUserId(UUID userId);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    long countByStatus(OrderStatus status);

    /**
     * Fetch a single order by ID with all items + products + address + user loaded eagerly.
     * Used in getOrderById to avoid separate lazy-load round-trips.
     */
    @EntityGraph(attributePaths = {"orderItems", "orderItems.product", "address", "user"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") UUID id);

    boolean existsByAddressId(UUID addressId);

    @EntityGraph(attributePaths = {"orderItems", "orderItems.product", "address", "user"})
    @Query("SELECT o FROM Order o WHERE " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:userId IS NULL OR o.user.id = :userId) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findOrdersWithFilters(
            @Param("status") OrderStatus status,
            @Param("userId") UUID userId,
            Pageable pageable);
}
