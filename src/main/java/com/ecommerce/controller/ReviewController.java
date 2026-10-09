package com.ecommerce.controller;

import com.ecommerce.dto.PaginatedResponseDto;
import com.ecommerce.dto.ReviewRequestDto;
import com.ecommerce.dto.ReviewResponseDto;
import com.ecommerce.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for Product Reviews management.
 *
 * GET /api/products/{productId}/reviews is publicly accessible.
 * POST, PUT, and DELETE endpoints require authentication.
 */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * Submits a new review for a product.
     */
    @PostMapping("/api/products/{productId}/reviews")
    public ResponseEntity<ReviewResponseDto> createReview(
            @PathVariable UUID productId,
            @Valid @RequestBody ReviewRequestDto dto) {

        ReviewResponseDto response = reviewService.createReview(productId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves paginated reviews for a product (publicly accessible).
     */
    @GetMapping("/api/products/{productId}/reviews")
    public ResponseEntity<PaginatedResponseDto<ReviewResponseDto>> getReviewsForProduct(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int safeSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, safeSize);
        Page<ReviewResponseDto> reviews = reviewService.getReviewsForProduct(productId, pageable);

        return ResponseEntity.ok(PaginatedResponseDto.fromPage(reviews));
    }

    /**
     * Updates the authenticated user's own review.
     */
    @PutMapping("/api/reviews/{reviewId}")
    public ResponseEntity<ReviewResponseDto> updateReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewRequestDto dto) {

        ReviewResponseDto response = reviewService.updateReview(reviewId, dto);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes the authenticated user's own review.
     */
    @DeleteMapping("/api/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable UUID reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.noContent().build();
    }
}
