package com.ecommerce.controller;

import com.ecommerce.dto.UpdateOrderStatusRequestDto;
import com.ecommerce.dto.UpdatePaymentStatusRequestDto;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Stage 6 Integration Tests:
 * Validates Order Management and Customer Order Tracking rules:
 * - Admin order inspection with customer details and address snapshot
 * - Valid & invalid order state transitions (strict 409 Conflict on illegal jumps)
 * - Terminal state invariants (DELIVERED and CANCELLED cannot transition)
 * - Single-time stock restoration on cancellation
 * - COD payment collection and prevention of payment on cancelled orders
 * - Customer ownership isolation (returns 404 when non-owner requests order)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class Stage6OrderManagementIntegrationTest {

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

    @Autowired
    private PaymentRepository paymentRepository;

    private User adminUser;
    private User customerA;
    private User customerB;
    private Product testProduct;
    private Address customerAddress;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail("admin-stage6-" + UUID.randomUUID() + "@example.com");
        adminUser.setFullName("Admin Officer");
        adminUser.setRole(UserRole.ROLE_ADMIN);
        adminUser.setEnabled(true);
        adminUser = userRepository.save(adminUser);

        customerA = new User();
        customerA.setId(UUID.randomUUID());
        customerA.setEmail("customera-stage6-" + UUID.randomUUID() + "@example.com");
        customerA.setFullName("Alice Customer");
        customerA.setRole(UserRole.ROLE_USER);
        customerA.setEnabled(true);
        customerA = userRepository.save(customerA);

        customerB = new User();
        customerB.setId(UUID.randomUUID());
        customerB.setEmail("customerb-stage6-" + UUID.randomUUID() + "@example.com");
        customerB.setFullName("Bob Customer");
        customerB.setRole(UserRole.ROLE_USER);
        customerB.setEnabled(true);
        customerB = userRepository.save(customerB);

        Category category = new Category();
        category.setName("Stage 6 Devices " + UUID.randomUUID());
        category = categoryRepository.save(category);

        testProduct = new Product();
        testProduct.setName("Stage 6 Smartphone");
        testProduct.setPrice(new BigDecimal("499.99"));
        testProduct.setStockQuantity(20);
        testProduct.setCategory(category);
        testProduct.setEnabled(true);
        testProduct = productRepository.save(testProduct);

        customerAddress = new Address();
        customerAddress.setUser(customerA);
        customerAddress.setStreet("742 Evergreen Terrace");
        customerAddress.setCity("Springfield");
        customerAddress.setState("OR");
        customerAddress.setZip("97477");
        customerAddress.setCountry("USA");
        customerAddress = addressRepository.save(customerAddress);

        testOrder = new Order();
        testOrder.setUser(customerA);
        testOrder.setAddress(customerAddress);
        testOrder.setStatus(OrderStatus.PENDING);
        testOrder.setTotalAmount(new BigDecimal("999.98"));

        OrderItem item = new OrderItem();
        item.setOrder(testOrder);
        item.setProduct(testProduct);
        item.setQuantity(2);
        item.setPriceAtPurchase(new BigDecimal("499.99"));
        testOrder.getOrderItems().add(item);

        testOrder = orderRepository.save(testOrder);

        Payment payment = new Payment();
        payment.setOrder(testOrder);
        payment.setMethod("COD");
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);
    }

    private JwtRequestPostProcessor adminJwt() {
        return jwt()
                .jwt(builder -> builder.subject(adminUser.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private JwtRequestPostProcessor customerAJwt() {
        return jwt()
                .jwt(builder -> builder.subject(customerA.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private JwtRequestPostProcessor customerBJwt() {
        return jwt()
                .jwt(builder -> builder.subject(customerB.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    @DisplayName("Admin can inspect order with permitted customer details, items snapshot, and COD status")
    void testAdminOrderInspectionDetails() throws Exception {
        mockMvc.perform(get("/api/admin/orders/" + testOrder.getId())
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testOrder.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(999.98))
                .andExpect(jsonPath("$.customerId").value(customerA.getId().toString()))
                .andExpect(jsonPath("$.customerEmail").value(customerA.getEmail()))
                .andExpect(jsonPath("$.customerName").value("Alice Customer"))
                .andExpect(jsonPath("$.paymentMethod").value("COD"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.address.street").value("742 Evergreen Terrace"))
                .andExpect(jsonPath("$.items[0].productName").value("Stage 6 Smartphone"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].priceAtPurchase").value(499.99));
    }

    @Test
    @DisplayName("Admin status transition: PENDING -> CONFIRMED -> SHIPPED -> DELIVERED succeeds")
    void testValidStatusProgression() throws Exception {
        // 1. PENDING -> CONFIRMED
        UpdateOrderStatusRequestDto dtoConfirmed = UpdateOrderStatusRequestDto.builder()
                .status(OrderStatus.CONFIRMED).build();
        mockMvc.perform(patch("/api/admin/orders/" + testOrder.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoConfirmed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 2. CONFIRMED -> SHIPPED
        UpdateOrderStatusRequestDto dtoShipped = UpdateOrderStatusRequestDto.builder()
                .status(OrderStatus.SHIPPED).build();
        mockMvc.perform(patch("/api/admin/orders/" + testOrder.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoShipped)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        // 3. SHIPPED -> DELIVERED
        UpdateOrderStatusRequestDto dtoDelivered = UpdateOrderStatusRequestDto.builder()
                .status(OrderStatus.DELIVERED).build();
        mockMvc.perform(patch("/api/admin/orders/" + testOrder.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoDelivered)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("Illegal transitions are strictly rejected with 409 Conflict")
    void testIllegalStatusTransitionsRejected() throws Exception {
        // PENDING -> DELIVERED (illegal skip)
        UpdateOrderStatusRequestDto dtoDelivered = UpdateOrderStatusRequestDto.builder()
                .status(OrderStatus.DELIVERED).build();
        mockMvc.perform(patch("/api/admin/orders/" + testOrder.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoDelivered)))
                .andExpect(status().isConflict());

        // PENDING -> SHIPPED (illegal skip)
        UpdateOrderStatusRequestDto dtoShipped = UpdateOrderStatusRequestDto.builder()
                .status(OrderStatus.SHIPPED).build();
        mockMvc.perform(patch("/api/admin/orders/" + testOrder.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoShipped)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Terminal states DELIVERED and CANCELLED cannot change status")
    void testTerminalStatesCannotTransition() throws Exception {
        // Set to DELIVERED
        testOrder.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(testOrder);

        UpdateOrderStatusRequestDto dtoCancel = UpdateOrderStatusRequestDto.builder()
                .status(OrderStatus.CANCELLED).build();
        mockMvc.perform(patch("/api/admin/orders/" + testOrder.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoCancel)))
                .andExpect(status().isConflict());

        // Set to CANCELLED
        testOrder.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(testOrder);

        UpdateOrderStatusRequestDto dtoConfirmed = UpdateOrderStatusRequestDto.builder()
                .status(OrderStatus.CONFIRMED).build();
        mockMvc.perform(patch("/api/admin/orders/" + testOrder.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoConfirmed)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Cancelling an order restores product stock exactly once; idempotent duplicate cancel does not double restore")
    void testStockRestorationOnCancellation() throws Exception {
        int initialStock = testProduct.getStockQuantity(); // 20

        UpdateOrderStatusRequestDto dtoCancel = UpdateOrderStatusRequestDto.builder()
                .status(OrderStatus.CANCELLED).build();

        // 1. First cancellation
        mockMvc.perform(patch("/api/admin/orders/" + testOrder.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoCancel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Product restoredProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(initialStock + 2, restoredProduct.getStockQuantity()); // 20 + 2 = 22

        // 2. Second cancellation attempt (idempotent NO-OP)
        mockMvc.perform(patch("/api/admin/orders/" + testOrder.getId() + "/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoCancel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Product secondCheckProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(initialStock + 2, secondCheckProduct.getStockQuantity()); // still 22, not 24!
    }

    @Test
    @DisplayName("Admin marks COD payment as collected via authorized endpoint; CANCELLED order cannot collect payment")
    void testCodPaymentCollection() throws Exception {
        UpdatePaymentStatusRequestDto dtoSuccess = UpdatePaymentStatusRequestDto.builder()
                .paymentStatus(PaymentStatus.SUCCESS).build();

        // 1. Mark as collected
        mockMvc.perform(put("/api/admin/orders/" + testOrder.getId() + "/payment-status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoSuccess)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());

        // 2. Cancel order and verify payment collection is blocked on cancelled order
        testOrder.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(testOrder);

        mockMvc.perform(put("/api/admin/orders/" + testOrder.getId() + "/payment-status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoSuccess)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Customer isolation: Customer A accesses own order (200), Customer B receives 404 (anti-enumeration)")
    void testCustomerOwnershipIsolation() throws Exception {
        // Customer A accesses own order
        mockMvc.perform(get("/api/orders/" + testOrder.getId())
                        .with(customerAJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testOrder.getId().toString()))
                .andExpect(jsonPath("$.totalAmount").value(999.98));

        // Customer B tries to access Customer A's order => strictly 404
        mockMvc.perform(get("/api/orders/" + testOrder.getId())
                        .with(customerBJwt()))
                .andExpect(status().isNotFound());
    }
}
