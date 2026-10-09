package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for returning product review responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponseDto {

    private UUID id;
    private UUID productId;
    private UUID userId;
    private String userName;
    private int rating;
    private String comment;
    private Instant createdAt;
}
