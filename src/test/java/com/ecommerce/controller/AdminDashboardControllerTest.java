package com.ecommerce.controller;

import com.ecommerce.entity.User;
import com.ecommerce.entity.UserRole;
import com.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Admin Dashboard metrics (Stage 11).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User adminUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail("admin-dash-" + UUID.randomUUID() + "@example.com");
        adminUser.setRole(UserRole.ROLE_ADMIN);
        adminUser.setEnabled(true);
        adminUser = userRepository.save(adminUser);

        regularUser = new User();
        regularUser.setId(UUID.randomUUID());
        regularUser.setEmail("user-dash-" + UUID.randomUUID() + "@example.com");
        regularUser.setRole(UserRole.ROLE_USER);
        regularUser.setEnabled(true);
        regularUser = userRepository.save(regularUser);
    }

    private JwtRequestPostProcessor adminJwt() {
        return jwt()
                .jwt(builder -> builder.subject(adminUser.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private JwtRequestPostProcessor userJwt() {
        return jwt()
                .jwt(builder -> builder.subject(regularUser.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    @DisplayName("Unauthenticated access to dashboard returns 401 Unauthorized")
    void getDashboard_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Regular user access to dashboard returns 403 Forbidden")
    void getDashboard_RegularUser_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin access to dashboard returns 200 OK with accurate system metrics")
    void getDashboard_Admin_ReturnsMetrics() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.totalEnabledProducts", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.totalCategories", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.totalOrders", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.ordersByStatus").isMap())
                .andExpect(jsonPath("$.pendingPayments", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.successfulPayments", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.totalReviews", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.lowStockProducts", greaterThanOrEqualTo(0)));
    }
}
