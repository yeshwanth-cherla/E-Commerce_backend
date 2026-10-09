package com.ecommerce.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SupabaseJwtAuthenticationConverterTest {

    private SupabaseJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SupabaseJwtAuthenticationConverter();
    }

    @Test
    void convert_WithAppMetadataAdminRole_ExtractsRoleAdmin() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject("123e4567-e89b-12d3-a456-426614174000")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("app_metadata", Map.of("role", "ROLE_ADMIN"))
                .build();

        AbstractAuthenticationToken auth = converter.convert(jwt);
        assertNotNull(auth);
        assertEquals("123e4567-e89b-12d3-a456-426614174000", auth.getName());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void convert_WithoutAppMetadataRole_DefaultsToRoleUser() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject("123e4567-e89b-12d3-a456-426614174000")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("user_metadata", Map.of("role", "ROLE_ADMIN")) // Must be ignored
                .build();

        AbstractAuthenticationToken auth = converter.convert(jwt);
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertFalse(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }
}
