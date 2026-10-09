package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Safe DTO representing authenticated user identity information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentityDto {

    private boolean authenticated;
    private UUID userId;
    private String email;
    private String role;
}
