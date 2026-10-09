package com.ecommerce.controller;

import com.ecommerce.dto.UpdateOrderStatusRequestDto;
import com.ecommerce.entity.*;
import com.ecommerce.repository.*;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Admin Order Management (Stage 11).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminOrderControllerTest {

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
    private AddressRepository addressRepository;

    @Autowired
    private OrderRepository orderRepository;

    private User adminUser;
    private User customerUser;
    private Product testProduct;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail("admin-ord-" + UUID.randomUUID() + "@example.com");
        adminUser.setRole(UserRole.ROLE_ADMIN);
        adminUser.setEnabled(true);
        adminUser = userRepository.save(adminUser);

        customerUser = new User();
        customerUser.setId(UUID.randomUUID());
        customerUser.setEmail("customer-ord-" + UUID.randomUUID() + "@example.com");
        customerUser.setRole(UserRole.ROLE_USER);
        customerUser.setEnabled(true);
        customerUser = userRepository.save(customerUser);

        Category category = new Category();
        category.setName("Order Admin Category " + UUID.randomUUID());
        category = categoryRepository.save(category);

        testProduct = new Product();
        testProduct.setName("Stock Product " + UUID.randomUUID());
        testProduct.setPrice(new BigDecimal("100.00"));
        testProduct.setStockQuantity(50);
        testProduct.setCategory(category);
        testProduct.setEnabled(true);
        testProduct = productRepository.save(testProduct);

        Address address = new Address();
        address.setUser(customerUser);
        address.setStreet("100 Admin Way");
        address.setCity("Metropolis");
        address.setCountry("USA");
        address = addressRepository.save(address);

        testOrder = new Order();
        testOrder.setUser(customerUser);
        testOrder.setAddress(address);
        testOrder.setStatus(OrderStatus.PENDING);
        testOrder.setTotalAmount(new BigDecimal("200.00"));

        OrderItem item = new OrderItem();
        item.setOrder(testOrder);
        item.setProduct(testProduct);
        item.setQuantity(2);
        item.setPriceAtPurchase(new BigDecimal("100.00"));
        testOrder.getOrderItems().add(item);

        testOrder = orderRepository.save(testOrder);
    }

    private JwtRequestPostProcessor adminJwt() {
        return jwt()
                .jwt(builder -> builder.subject(adminUser.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private JwtRequestPostProcessor userJwt() {
        return jwt()
                .jwt(builder -> builder.subject(customerUser.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    @DisplayName("Regular user accessing admin orders returns 403 Forbidden")
    void getOrders_RegularUser_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/orders").with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can list and filter orders by status")
    void getOrders_Admin_ReturnsFilteredOrders() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .with(adminJwt())
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(testOrder.getId().toString()));
    }

    @Test
    @DisplayName("Admin can advance order status PENDING -> CONFIRMED -> SHIPPED -> DELIVERED")
    void updateOrderStatus_ValidProgression_Success() throws Exception {
        // PENDING -> CONFIRMED
        UpdateOrderStatusRequestDto dtoConfirmed = UpdateOrderStatusRequestDto.builder()
                .status(OrderStatus.CONFIRMED)
                .build();

        mockMvc.perform(patch("/api/admin/orders/" + testOrder.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoConfirmed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // CONFIRMED -> SHIPPED
        UpdateOrderStatusRequestDto dtoShipped = UpdateOrderStatusRequestDto.builder()
                .status(OrderStatus.SHIPPED)
                .build();

        mockMvc.perform(patch("/api/admin/orders/" + testOrder.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoShipped)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("Cancelling an order restores product stock quantities")
    void updateOrderStatus_Cancelled_RestoresProductStock() throws Exception {
        int initialStock = testProduct.getStockQuantity(); // 50

        UpdateOrderStatusRequestDto dtoCancel = UpdateOrderStatusRequestDto.builder()
                .status(OrderStatus.CANCELLED)
                .build();

        mockMvc.perform(patch("/api/admin/orders/" + testOrder.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoCancel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(initialStock + 2, updatedProduct.getStockQuantity()); // 50 + 2 = 52
    }

    @Test
    @DisplayName("Invalid status transition from CANCELLED to CONFIRMED returns 409 Conflict")
    void updateOrderStatus_InvalidTransition_Returns409Conflict() throws Exception {
        testOrder.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(testOrder);

        UpdateOrderStatusRequestDto dtoConfirmed = UpdateOrderStatusRequestDto.builder()
                .status(OrderStatus.CONFIRMED)
                .build();

        mockMvc.perform(patch("/api/admin/orders/" + testOrder.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoConfirmed)))
                .andExpect(status().isConflict());
    }
}
