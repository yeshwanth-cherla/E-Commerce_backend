package com.ecommerce.controller;

import com.ecommerce.dto.UpdateUserStatusRequestDto;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserRole;
import com.ecommerce.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Admin User Management (Stage 11).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private User adminUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail("admin-usr-" + UUID.randomUUID() + "@example.com");
        adminUser.setFullName("Admin User");
        adminUser.setRole(UserRole.ROLE_ADMIN);
        adminUser.setEnabled(true);
        adminUser = userRepository.save(adminUser);

        regularUser = new User();
        regularUser.setId(UUID.randomUUID());
        regularUser.setEmail("target-usr-" + UUID.randomUUID() + "@example.com");
        regularUser.setFullName("Target Customer");
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
    @DisplayName("Regular user request to list users returns 403 Forbidden")
    void getUsers_RegularUser_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can search users and receive safe profile response without passwords")
    void getUsers_Admin_ReturnsPaginatedUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(adminJwt())
                        .param("search", "Target Customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].email").value(regularUser.getEmail()))
                .andExpect(jsonPath("$.content[0].fullName").value("Target Customer"))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    @DisplayName("Admin can view a single user's safe profile")
    void getUserById_Admin_ReturnsUserDetail() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + regularUser.getId()).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(regularUser.getId().toString()))
                .andExpect(jsonPath("$.email").value(regularUser.getEmail()));
    }

    @Test
    @DisplayName("Admin can disable a customer account")
    void updateUserStatus_DisableUser_Success() throws Exception {
        UpdateUserStatusRequestDto dto = UpdateUserStatusRequestDto.builder()
                .enabled(false)
                .build();

        mockMvc.perform(patch("/api/admin/users/" + regularUser.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        User updated = userRepository.findById(regularUser.getId()).orElseThrow();
        assertFalse(updated.isEnabled());
    }

    @Test
    @DisplayName("Admin attempting to disable their own account returns 409 Conflict")
    void updateUserStatus_AdminSelfDisable_Returns409Conflict() throws Exception {
        UpdateUserStatusRequestDto dto = UpdateUserStatusRequestDto.builder()
                .enabled(false)
                .build();

        mockMvc.perform(patch("/api/admin/users/" + adminUser.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());

        User unchanged = userRepository.findById(adminUser.getId()).orElseThrow();
        assertTrue(unchanged.isEnabled());
    }
}
