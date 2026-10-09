package com.ecommerce.dto;

import com.ecommerce.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe user profile response for administrative management.
 * Contains no credentials, tokens, or sensitive authentication details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {

    private UUID id;
    private String email;
    private String fullName;
    private String phone;
    private UserRole role;
    private boolean enabled;
    private Instant createdAt;
}
