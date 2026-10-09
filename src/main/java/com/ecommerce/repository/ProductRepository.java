package com.ecommerce.repository;

import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByEnabledTrue(Pageable pageable);

    Page<Product> findByCategoryAndEnabledTrue(Category category, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndEnabledTrue(String name, Pageable pageable);

    long countByCategoryId(Long categoryId);

    long countByEnabledTrue();

    long countByStockQuantityLessThanEqual(int threshold);

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT p FROM Product p WHERE " +
           "(:enabledOnly = false OR p.enabled = true) AND " +
           "(:categoryId IS NULL OR p.category.id = :categoryId OR p.category.parent.id = :categoryId) AND " +
           "(CAST(:search AS string) IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Product> findProductsWithFilters(
            @Param("search") String search,
            @Param("categoryId") Long categoryId,
            @Param("enabledOnly") boolean enabledOnly,
            Pageable pageable);

    /**
     * Acquires a PostgreSQL row-level FOR UPDATE lock on the requested products.
     *
     * Used exclusively during checkout to prevent concurrent overselling:
     * two simultaneous checkouts touching the same product will serialize here,
     * so stock validation and decrement are always consistent.
     *
     * The results are ordered by id to establish a deterministic lock-acquisition
     * order and avoid deadlocks between concurrent transactions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    List<Product> findAllByIdWithPessimisticLock(@Param("ids") List<UUID> ids);

    /**
     * Acquires a PostgreSQL row-level FOR UPDATE lock on a single product.
     * Used during manual stock adjustments to prevent race conditions with checkouts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    java.util.Optional<Product> findByIdWithPessimisticLock(@Param("id") UUID id);

    /**
     * Finds products with stock quantity at or below a specified threshold, with optional search and category filters.
     */
    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT p FROM Product p WHERE " +
           "p.stockQuantity <= :threshold AND " +
           "(:categoryId IS NULL OR p.category.id = :categoryId OR p.category.parent.id = :categoryId) AND " +
           "(CAST(:search AS string) IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Product> findLowStockProducts(
            @Param("threshold") int threshold,
            @Param("search") String search,
            @Param("categoryId") Long categoryId,
            Pageable pageable);
}
