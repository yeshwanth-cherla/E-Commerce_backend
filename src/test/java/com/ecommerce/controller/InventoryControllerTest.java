package com.ecommerce.controller;

import com.ecommerce.dto.InventoryAdjustmentRequestDto;
import com.ecommerce.entity.*;
import com.ecommerce.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Database Integration Tests for Stage 09 — Inventory Module.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryAdjustmentRepository inventoryAdjustmentRepository;

    private User adminUser;
    private User regularUser;
    private Category categoryElectronics;
    private Product productLowStock;
    private Product productHighStock;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail("admin-inv-" + UUID.randomUUID() + "@example.com");
        adminUser.setRole(UserRole.ROLE_ADMIN);
        adminUser.setEnabled(true);
        adminUser = userRepository.save(adminUser);

        regularUser = new User();
        regularUser.setId(UUID.randomUUID());
        regularUser.setEmail("user-inv-" + UUID.randomUUID() + "@example.com");
        regularUser.setRole(UserRole.ROLE_USER);
        regularUser.setEnabled(true);
        regularUser = userRepository.save(regularUser);

        categoryElectronics = new Category();
        categoryElectronics.setName("Inventory Tech " + UUID.randomUUID());
        categoryElectronics.setDescription("Inventory test category");
        categoryElectronics = categoryRepository.save(categoryElectronics);

        productLowStock = new Product();
        productLowStock.setName("Low Stock Widget " + UUID.randomUUID());
        productLowStock.setDescription("Widget in limited supply");
        productLowStock.setPrice(new BigDecimal("49.99"));
        productLowStock.setStockQuantity(3);
        productLowStock.setCategory(categoryElectronics);
        productLowStock.setEnabled(true);
        productLowStock = productRepository.save(productLowStock);

        productHighStock = new Product();
        productHighStock.setName("High Stock Gadget " + UUID.randomUUID());
        productHighStock.setDescription("Gadget in abundance");
        productHighStock.setPrice(new BigDecimal("199.99"));
        productHighStock.setStockQuantity(100);
        productHighStock.setCategory(categoryElectronics);
        productHighStock.setEnabled(true);
        productHighStock = productRepository.save(productHighStock);
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

    // ------------------------------------------------------------------ //
    // 1. Authorization Controls                                           //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("Authorization & Security Controls")
    class AuthorizationTests {

        @Test
        @DisplayName("Unauthenticated request to inventory returns 401")
        void unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/admin/inventory"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Regular user request to view inventory returns 403 Forbidden")
        void regularUser_ViewInventory_Returns403() throws Exception {
            mockMvc.perform(get("/api/admin/inventory").with(userJwt()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Regular user request to adjust stock returns 403 Forbidden")
        void regularUser_AdjustStock_Returns403() throws Exception {
            InventoryAdjustmentRequestDto dto = InventoryAdjustmentRequestDto.builder()
                    .productId(productLowStock.getId())
                    .type(AdjustmentType.ADD)
                    .amount(10)
                    .reason("Stock arrival")
                    .build();

            mockMvc.perform(post("/api/admin/inventory/adjust")
                            .with(userJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin user request to view inventory returns 200 OK")
        void adminUser_ViewInventory_Returns200() throws Exception {
            mockMvc.perform(get("/api/admin/inventory").with(adminJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    // ------------------------------------------------------------------ //
    // 2. Inventory Visibility & Low Stock                                 //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("Inventory Listing & Low Stock Monitoring")
    class VisibilityTests {

        @Test
        @DisplayName("Get inventory returns paginated products with low-stock flags")
        void getInventory_ReturnsPaginatedProducts() throws Exception {
            mockMvc.perform(get("/api/admin/inventory")
                            .with(adminJwt())
                            .param("threshold", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.page").value(0));
        }

        @Test
        @DisplayName("Get low stock products filters items at or below threshold")
        void getLowStockProducts_FiltersByThreshold() throws Exception {
            mockMvc.perform(get("/api/admin/inventory/low-stock")
                            .with(adminJwt())
                            .param("threshold", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[*].id", hasItem(productLowStock.getId().toString())))
                    .andExpect(jsonPath("$.content[*].id", not(hasItem(productHighStock.getId().toString()))));
        }
    }

    // ------------------------------------------------------------------ //
    // 3. Stock Adjustments                                                //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("Stock Adjustment Operations")
    class AdjustmentTests {

        @Test
        @DisplayName("SET adjustment overrides product stock and creates audit record")
        void adjustStock_SetType_UpdatesStockAndCreatesAuditLog() throws Exception {
            InventoryAdjustmentRequestDto dto = InventoryAdjustmentRequestDto.builder()
                    .productId(productLowStock.getId())
                    .type(AdjustmentType.SET)
                    .amount(25)
                    .reason("Stock count correction")
                    .build();

            mockMvc.perform(post("/api/admin/inventory/adjust")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId").value(productLowStock.getId().toString()))
                    .andExpect(jsonPath("$.previousStock").value(3))
                    .andExpect(jsonPath("$.newStock").value(25))
                    .andExpect(jsonPath("$.quantityDelta").value(22))
                    .andExpect(jsonPath("$.adjustmentType").value("SET"))
                    .andExpect(jsonPath("$.reason").value("Stock count correction"));

            Product updatedProduct = productRepository.findById(productLowStock.getId()).orElseThrow();
            assertEquals(25, updatedProduct.getStockQuantity());
        }

        @Test
        @DisplayName("ADD adjustment increments product stock")
        void adjustStock_AddType_IncrementsStock() throws Exception {
            InventoryAdjustmentRequestDto dto = InventoryAdjustmentRequestDto.builder()
                    .productId(productLowStock.getId())
                    .type(AdjustmentType.ADD)
                    .amount(10)
                    .reason("Restock shipment received")
                    .build();

            mockMvc.perform(post("/api/admin/inventory/adjust")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.previousStock").value(3))
                    .andExpect(jsonPath("$.newStock").value(13))
                    .andExpect(jsonPath("$.quantityDelta").value(10));

            Product updatedProduct = productRepository.findById(productLowStock.getId()).orElseThrow();
            assertEquals(13, updatedProduct.getStockQuantity());
        }

        @Test
        @DisplayName("SUBTRACT adjustment decrements product stock")
        void adjustStock_SubtractType_DecrementsStock() throws Exception {
            InventoryAdjustmentRequestDto dto = InventoryAdjustmentRequestDto.builder()
                    .productId(productHighStock.getId())
                    .type(AdjustmentType.SUBTRACT)
                    .amount(30)
                    .reason("Damaged stock written off")
                    .build();

            mockMvc.perform(post("/api/admin/inventory/adjust")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.previousStock").value(100))
                    .andExpect(jsonPath("$.newStock").value(70))
                    .andExpect(jsonPath("$.quantityDelta").value(-30));

            Product updatedProduct = productRepository.findById(productHighStock.getId()).orElseThrow();
            assertEquals(70, updatedProduct.getStockQuantity());
        }

        @Test
        @DisplayName("Adjustment reducing stock below zero fails with 409 Conflict")
        void adjustStock_NegativeStock_Returns409Conflict() throws Exception {
            InventoryAdjustmentRequestDto dto = InventoryAdjustmentRequestDto.builder()
                    .productId(productLowStock.getId())
                    .type(AdjustmentType.SUBTRACT)
                    .amount(10) // current stock is 3, 3 - 10 = -7
                    .reason("Over-deduction test")
                    .build();

            mockMvc.perform(post("/api/admin/inventory/adjust")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isConflict());

            Product unchangedProduct = productRepository.findById(productLowStock.getId()).orElseThrow();
            assertEquals(3, unchangedProduct.getStockQuantity());
        }

        @Test
        @DisplayName("Adjustment with missing product ID returns 404 Not Found")
        void adjustStock_NonExistentProduct_Returns404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();
            InventoryAdjustmentRequestDto dto = InventoryAdjustmentRequestDto.builder()
                    .productId(nonExistentId)
                    .type(AdjustmentType.SET)
                    .amount(10)
                    .reason("Invalid product adjustment")
                    .build();

            mockMvc.perform(post("/api/admin/inventory/adjust")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Adjustment with blank reason fails with 400 Bad Request")
        void adjustStock_BlankReason_Returns400() throws Exception {
            InventoryAdjustmentRequestDto dto = InventoryAdjustmentRequestDto.builder()
                    .productId(productLowStock.getId())
                    .type(AdjustmentType.ADD)
                    .amount(5)
                    .reason("  ")
                    .build();

            mockMvc.perform(post("/api/admin/inventory/adjust")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------------ //
    // 4. Audit Trail History                                             //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("Audit History Tracking")
    class AuditTrailTests {

        @Test
        @DisplayName("Get adjustment history returns audit records")
        void getAdjustmentHistory_ReturnsRecords() throws Exception {
            // First adjust stock to generate audit log
            InventoryAdjustmentRequestDto dto = InventoryAdjustmentRequestDto.builder()
                    .productId(productLowStock.getId())
                    .type(AdjustmentType.ADD)
                    .amount(5)
                    .reason("Audit test restock")
                    .build();

            mockMvc.perform(post("/api/admin/inventory/adjust")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());

            // Retrieve history for specific product
            mockMvc.perform(get("/api/admin/inventory/adjustments")
                            .with(adminJwt())
                            .param("productId", productLowStock.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].productId").value(productLowStock.getId().toString()))
                    .andExpect(jsonPath("$.content[0].reason").value("Audit test restock"));
        }
    }
}
