package com.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * E-Commerce Platform — Spring Boot Application Entry Point
 *
 * Architecture:
 *   React Frontend
 *     → Supabase Auth (authentication)
 *     → Spring Boot REST API  (this application)
 *       → Spring Security (OAuth2 Resource Server — JWT validation via Supabase JWKS)
 *       → Service Layer (business logic)
 *       → Spring Data JPA / Hibernate
 *       → Supabase PostgreSQL
 */
@SpringBootApplication
public class EcommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceApplication.class, args);
    }
}
