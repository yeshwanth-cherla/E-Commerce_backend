package com.ecommerce.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom JWT converter for Supabase-issued tokens.
 *
 * Extracts trusted user authorization roles from app_metadata (set server-side by Supabase/Admin).
 *
 * SECURITY RULE:
 *   - NEVER use user_metadata as an authorization source (user_metadata is user-editable).
 *   - Uses app_metadata.role if present; defaults to ROLE_USER.
 */
@Component
public class SupabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private List<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        String roleStr = null;

        // 1. Inspect trusted app_metadata claim (Map<String, Object>)
        Map<String, Object> appMetadata = jwt.getClaim("app_metadata");
        if (appMetadata != null && appMetadata.containsKey("role")) {
            Object roleObj = appMetadata.get("role");
            if (roleObj instanceof String) {
                roleStr = (String) roleObj;
            }
        }

        // 2. Direct claim fallback check if custom claim exists
        if (roleStr == null && jwt.hasClaim("user_role")) {
            roleStr = jwt.getClaimAsString("user_role");
        }

        // Default role if not specified in trusted app_metadata
        if (roleStr == null || roleStr.isBlank()) {
            roleStr = "ROLE_USER";
        }

        // Ensure ROLE_ prefix for Spring Security authority convention
        if (!roleStr.startsWith("ROLE_")) {
            roleStr = "ROLE_" + roleStr.toUpperCase();
        }

        authorities.add(new SimpleGrantedAuthority(roleStr));
        return authorities;
    }
}
