package com.ecommerce.security;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityJwtAndUserStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User enabledUser;
    private User disabledUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        enabledUser = new User();
        enabledUser.setId(UUID.randomUUID());
        enabledUser.setEmail("enabled-" + UUID.randomUUID() + "@example.com");
        enabledUser.setFullName("Enabled User");
        enabledUser.setRole(UserRole.ROLE_USER);
        enabledUser.setEnabled(true);
        enabledUser = userRepository.save(enabledUser);

        disabledUser = new User();
        disabledUser.setId(UUID.randomUUID());
        disabledUser.setEmail("disabled-" + UUID.randomUUID() + "@example.com");
        disabledUser.setFullName("Disabled User");
        disabledUser.setRole(UserRole.ROLE_USER);
        disabledUser.setEnabled(false);
        disabledUser = userRepository.save(disabledUser);

        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail("admin-" + UUID.randomUUID() + "@example.com");
        adminUser.setFullName("Admin User");
        adminUser.setRole(UserRole.ROLE_ADMIN);
        adminUser.setEnabled(true);
        adminUser = userRepository.save(adminUser);
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401 Unauthorized")
    void unauthenticatedRequest_Returns401() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Enabled user with valid JWT can access protected user endpoint")
    void enabledUser_CanAccessProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/addresses")
                        .with(jwt()
                                .jwt(builder -> builder.subject(enabledUser.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Disabled user with valid JWT receives 403 Forbidden")
    void disabledUser_Receives403Forbidden() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .with(jwt()
                                .jwt(builder -> builder.subject(disabledUser.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("User account is disabled"));
    }

    @Test
    @DisplayName("Non-admin user accessing admin endpoint receives 403 Access Denied")
    void regularUser_AccessingAdminEndpoint_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .with(jwt()
                                .jwt(builder -> builder.subject(enabledUser.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin user accessing admin endpoint is authorized")
    void adminUser_AccessingAdminEndpoint_Allowed() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .with(jwt()
                                .jwt(builder -> builder.subject(adminUser.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
