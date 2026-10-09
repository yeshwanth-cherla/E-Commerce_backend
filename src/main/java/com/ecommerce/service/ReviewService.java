package com.ecommerce.service;

import com.ecommerce.dto.ReviewRequestDto;
import com.ecommerce.dto.ReviewResponseDto;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.Review;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceConflictException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ReviewRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles product review lifecycle operations and aggregate average rating calculations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * Submits a new review for a product.
     */
    @Transactional
    public ReviewResponseDto createReview(UUID productId, ReviewRequestDto dto) {
        UUID currentUserId = requireCurrentUserId();

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id : '" + currentUserId + "'"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id : '" + productId + "'"));

        if (!product.isEnabled()) {
            throw new ResourceConflictException("Cannot review a disabled product");
        }

        if (reviewRepository.existsByUserIdAndProductId(currentUserId, productId)) {
            throw new ResourceConflictException("You have already reviewed this product");
        }

        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setRating(dto.getRating());
        review.setComment(dto.getComment());

        Review savedReview = reviewRepository.save(review);
        recalculateProductAverageRating(product);

        log.info("Review created: reviewId={}, productId={}, userId={}, rating={}",
                savedReview.getId(), productId, currentUserId, dto.getRating());

        return mapToReviewResponseDto(savedReview);
    }

    /**
     * Returns paginated reviews for a specific product.
     */
    @Transactional(readOnly = true)
    public Page<ReviewResponseDto> getReviewsForProduct(UUID productId, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with id : '" + productId + "'");
        }

        return reviewRepository.findByProductId(productId, pageable)
                .map(this::mapToReviewResponseDto);
    }

    /**
     * Updates an existing review owned by the current user.
     */
    @Transactional
    public ReviewResponseDto updateReview(UUID reviewId, ReviewRequestDto dto) {
        UUID currentUserId = requireCurrentUserId();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id : '" + reviewId + "'"));

        if (!review.getUser().getId().equals(currentUserId)) {
            throw new ResourceNotFoundException("Review not found with id : '" + reviewId + "'");
        }

        review.setRating(dto.getRating());
        review.setComment(dto.getComment());

        Review updatedReview = reviewRepository.save(review);
        recalculateProductAverageRating(review.getProduct());

        log.info("Review updated: reviewId={}, rating={}", reviewId, dto.getRating());

        return mapToReviewResponseDto(updatedReview);
    }

    /**
     * Deletes a review owned by the current user.
     */
    @Transactional
    public void deleteReview(UUID reviewId) {
        UUID currentUserId = requireCurrentUserId();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id : '" + reviewId + "'"));

        if (!review.getUser().getId().equals(currentUserId)) {
            throw new ResourceNotFoundException("Review not found with id : '" + reviewId + "'");
        }

        Product product = review.getProduct();
        reviewRepository.delete(review);
        recalculateProductAverageRating(product);

        log.info("Review deleted: reviewId={}, productId={}", reviewId, product.getId());
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                            //
    // ------------------------------------------------------------------ //

    private void recalculateProductAverageRating(Product product) {
        Optional<Double> avg = reviewRepository.calculateAverageRatingByProductId(product.getId());
        if (avg.isPresent() && avg.get() != null) {
            BigDecimal rounded = BigDecimal.valueOf(avg.get()).setScale(2, RoundingMode.HALF_UP);
            product.setAverageRating(rounded);
        } else {
            product.setAverageRating(null);
        }
        productRepository.save(product);
    }

    private UUID requireCurrentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
    }

    private ReviewResponseDto mapToReviewResponseDto(Review review) {
        String userName = review.getUser().getFullName();
        if (userName == null || userName.isBlank()) {
            userName = review.getUser().getEmail();
        }

        return ReviewResponseDto.builder()
                .id(review.getId())
                .productId(review.getProduct().getId())
                .userId(review.getUser().getId())
                .userName(userName)
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
