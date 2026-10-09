package com.ecommerce.entity;

/**
 * Application user roles used for role-based authorization.
 *
 * ROLE_USER  — Standard customer account
 * ROLE_ADMIN — Administrator account with elevated privileges
 *
 * These role names are prefixed with ROLE_ as required by Spring Security's
 * hasRole() expressions (Spring Security strips the prefix internally).
 *
 * In Stage 05, these roles will be embedded in the Supabase JWT via
 * app_metadata.role and read by the Spring Security JWT converter.
 */
public enum UserRole {
    ROLE_USER,
    ROLE_ADMIN
}
