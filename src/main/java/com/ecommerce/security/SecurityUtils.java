package com.ecommerce.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

/**
 * Reusable Security Context utility for extracting authenticated user details.
 *
 * Pulls validated identity claims directly from the Spring SecurityContext (JWT).
 * NEVER trusts client-supplied user IDs in request parameters or headers.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Private constructor for utility class
    }

    /**
     * Obtains the authenticated user's UUID (matching Supabase auth.users.id).
     *
     * @return Optional containing the UUID if authenticated, or empty if anonymous.
     */
    public static Optional<UUID> getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                return Optional.of(UUID.fromString(jwt.getSubject()));
            } catch (IllegalArgumentException e) {
                // If sub is not a valid UUID string
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Obtains the authenticated user's email address from JWT claims.
     *
     * @return Optional containing email if authenticated and present, or empty.
     */
    public static Optional<String> getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String email = jwt.getClaimAsString("email");
            return Optional.ofNullable(email);
        }
        return Optional.empty();
    }

    /**
     * Obtains the authenticated user's primary GrantedAuthority role string.
     *
     * @return Optional containing role name (e.g. ROLE_USER, ROLE_ADMIN).
     */
    public static Optional<String> getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !authentication.getAuthorities().isEmpty()) {
            return Optional.of(authentication.getAuthorities().iterator().next().getAuthority());
        }
        return Optional.empty();
    }
}
