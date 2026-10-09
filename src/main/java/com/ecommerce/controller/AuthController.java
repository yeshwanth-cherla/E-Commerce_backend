package com.ecommerce.controller;

import com.ecommerce.dto.UserIdentityDto;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Authentication Verification REST Controller
 *
 * Exposes /api/auth/me for verifying Supabase JWT authentication status
 * and retrieving the authenticated user's principal details.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<UserIdentityDto> getCurrentUserIdentity() {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user identity not found in token"));

        String email = SecurityUtils.getCurrentUserEmail().orElse("N/A");
        String role = SecurityUtils.getCurrentUserRole().orElse("ROLE_USER");

        UserIdentityDto identity = UserIdentityDto.builder()
                .authenticated(true)
                .userId(userId)
                .email(email)
                .role(role)
                .build();

        return ResponseEntity.ok(identity);
    }
}
