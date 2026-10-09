package com.ecommerce.controller;

import com.ecommerce.dto.AdminDashboardResponseDto;
import com.ecommerce.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for the Admin System Dashboard.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * Returns aggregate system metrics for administrator monitoring.
     */
    @GetMapping
    public ResponseEntity<AdminDashboardResponseDto> getDashboardMetrics() {
        AdminDashboardResponseDto metrics = adminDashboardService.getDashboardMetrics();
        return ResponseEntity.ok(metrics);
    }
}
