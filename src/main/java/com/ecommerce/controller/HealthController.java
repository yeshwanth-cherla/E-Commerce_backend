package com.ecommerce.controller;

import com.ecommerce.dto.HealthResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Health Check REST Controller
 *
 * Provides a minimal health endpoint for verifying backend runtime status.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<HealthResponseDto> getHealthStatus() {
        HealthResponseDto healthResponse = HealthResponseDto.builder()
                .status("UP")
                .service("ecommerce-backend")
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.ok(healthResponse);
    }
}
