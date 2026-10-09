package com.ecommerce.repository;

import com.ecommerce.entity.Product;
import com.ecommerce.entity.Review;
import com.ecommerce.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByProduct(Product product, Pageable pageable);

    Page<Review> findByProductId(UUID productId, Pageable pageable);

    Optional<Review> findByUserAndProduct(User user, Product product);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    /**
     * Calculates the average rating for a product.
     * Used to update Product.averageRating after a review change.
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    Optional<Double> calculateAverageRatingByProductId(@Param("productId") UUID productId);
}
