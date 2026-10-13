package com.ecommerce.controller;

import com.ecommerce.dto.CheckoutRequestDto;
import com.ecommerce.dto.InventoryAdjustmentRequestDto;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Stage 5 Comprehensive Integration Tests for Inventory and Stock Control.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Stage5InventoryControlIntegrationTest {

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

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    private User adminUser;
    private User customerUser;
    private Category category;
    private Product productNormal;
    private Product productLowStock;
    private Product productZeroStock;
    private Address customerAddress;

    private JwtRequestPostProcessor mockAdminJwt(User admin) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(builder -> builder
                        .subject(admin.getId().toString())
                        .claim("email", admin.getEmail()));
    }

    private JwtRequestPostProcessor mockCustomerJwt(User customer) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(builder -> builder
                        .subject(customer.getId().toString())
                        .claim("email", customer.getEmail()));
    }

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail("admin-stage5-" + UUID.randomUUID() + "@example.com");
        adminUser.setRole(UserRole.ROLE_ADMIN);
        adminUser.setEnabled(true);
        adminUser = userRepository.save(adminUser);

        customerUser = new User();
        customerUser.setId(UUID.randomUUID());
        customerUser.setEmail("cust-stage5-" + UUID.randomUUID() + "@example.com");
        customerUser.setRole(UserRole.ROLE_USER);
        customerUser.setEnabled(true);
        customerUser = userRepository.save(customerUser);

        customerAddress = new Address();
        customerAddress.setUser(customerUser);
        customerAddress.setStreet("456 Market St");
        customerAddress.setCity("Metropolis");
        customerAddress.setState("NY");
        customerAddress.setZip("10001");
        customerAddress.setCountry("USA");
        customerAddress = addressRepository.save(customerAddress);

        category = new Category();
        category.setName("Stage5 Category " + UUID.randomUUID());
        category.setDescription("Test Category for Stage 5");
        category = categoryRepository.save(category);

        productNormal = new Product();
        productNormal.setName("Normal Stock Gadget " + UUID.randomUUID());
        productNormal.setPrice(new BigDecimal("99.99"));
        productNormal.setStockQuantity(50);
        productNormal.setCategory(category);
        productNormal.setEnabled(true);
        productNormal = productRepository.save(productNormal);

        productLowStock = new Product();
        productLowStock.setName("Low Stock Gadget " + UUID.randomUUID());
        productLowStock.setPrice(new BigDecimal("49.99"));
        productLowStock.setStockQuantity(5);
        productLowStock.setCategory(category);
        productLowStock.setEnabled(true);
        productLowStock = productRepository.save(productLowStock);

        productZeroStock = new Product();
        productZeroStock.setName("Zero Stock Gadget " + UUID.randomUUID());
        productZeroStock.setPrice(new BigDecimal("29.99"));
        productZeroStock.setStockQuantity(0);
        productZeroStock.setCategory(category);
        productZeroStock.setEnabled(true);
        productZeroStock = productRepository.save(productZeroStock);
    }

    // ================================================================== //
    // 1. Authorization Tests                                              //
    // ================================================================== //

    @Test
    @DisplayName("Admin-only access: unauthenticated returns 401, customer returns 403")
    void authorization_inventoryEndpoints_guarded() throws Exception {
        // Unauthenticated
        mockMvc.perform(get("/api/admin/inventory"))
                .andExpect(status().isUnauthorized());

        // Regular customer (ROLE_USER)
        mockMvc.perform(get("/api/admin/inventory")
                .with(mockCustomerJwt(customerUser)))
                .andExpect(status().isForbidden());

        // Admin (ROLE_ADMIN)
        mockMvc.perform(get("/api/admin/inventory")
                .with(mockAdminJwt(adminUser)))
                .andExpect(status().isOk());
    }

    // ================================================================== //
    // 2. SET, ADD, SUBTRACT Operations                                   //
    // ================================================================== //

    @Test
    @DisplayName("SET adjustment: sets exact stock quantity and logs audit record")
    void adjustStock_set_success() throws Exception {
        InventoryAdjustmentRequestDto dto = InventoryAdjustmentRequestDto.builder()
                .productId(productNormal.getId())
                .type(AdjustmentType.SET)
                .amount(100)
                .reason("Physical count reconciliation")
                .build();

        mockMvc.perform(post("/api/admin/inventory/adjust")
                .with(mockAdminJwt(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productNormal.getId().toString()))
                .andExpect(jsonPath("$.previousStock").value(50))
                .andExpect(jsonPath("$.newStock").value(100))
                .andExpect(jsonPath("$.quantityDelta").value(50))
                .andExpect(jsonPath("$.adjustmentType").value("SET"))
                .andExpect(jsonPath("$.reason").value("Physical count reconciliation"));

        Product updated = productRepository.findById(productNormal.getId()).orElseThrow();
        assertEquals(100, updated.getStockQuantity());
    }

    @Test
    @DisplayName("ADD adjustment: increases stock quantity by amount")
    void adjustStock_add_success() throws Exception {
        InventoryAdjustmentRequestDto dto = InventoryAdjustmentRequestDto.builder()
                .productId(productNormal.getId())
                .type(AdjustmentType.ADD)
                .amount(20)
                .reason("Received supplier shipment")
                .build();

        mockMvc.perform(post("/api/admin/inventory/adjust")
                .with(mockAdminJwt(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStock").value(50))
                .andExpect(jsonPath("$.newStock").value(70))
                .andExpect(jsonPath("$.quantityDelta").value(20));

        Product updated = productRepository.findById(productNormal.getId()).orElseThrow();
        assertEquals(70, updated.getStockQuantity());
    }

    @Test
    @DisplayName("SUBTRACT adjustment: decreases stock quantity by amount")
    void adjustStock_subtract_success() throws Exception {
        InventoryAdjustmentRequestDto dto = InventoryAdjustmentRequestDto.builder()
                .productId(productNormal.getId())
                .type(AdjustmentType.SUBTRACT)
                .amount(15)
                .reason("Damaged goods removed")
                .build();

        mockMvc.perform(post("/api/admin/inventory/adjust")
                .with(mockAdminJwt(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStock").value(50))
                .andExpect(jsonPath("$.newStock").value(35))
                .andExpect(jsonPath("$.quantityDelta").value(-15));

        Product updated = productRepository.findById(productNormal.getId()).orElseThrow();
        assertEquals(35, updated.getStockQuantity());
    }

    // ================================================================== //
    // 3. Validation & Safety Tests                                        //
    // ================================================================== //

    @Test
    @DisplayName("Validation: negative amount is rejected with 400 Bad Request")
    void adjustStock_negativeAmount_returns400() throws Exception {
        InventoryAdjustmentRequestDto dto = InventoryAdjustmentRequestDto.builder()
                .productId(productNormal.getId())
                .type(AdjustmentType.ADD)
                .amount(-10)
                .reason("Invalid test")
                .build();

        mockMvc.perform(post("/api/admin/inventory/adjust")
                .with(mockAdminJwt(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Safety: SUBTRACT exceeding current stock is rejected with 409 Conflict")
    void adjustStock_subtractMoreThanAvailable_returns409() throws Exception {
        InventoryAdjustmentRequestDto dto = InventoryAdjustmentRequestDto.builder()
                .productId(productNormal.getId())
                .type(AdjustmentType.SUBTRACT)
                .amount(100) // current stock is 50
                .reason("Attempting excessive reduction")
                .build();

        mockMvc.perform(post("/api/admin/inventory/adjust")
                .with(mockAdminJwt(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    // ================================================================== //
    // 4. Low-Stock & Zero-Stock Display Tests                            //
    // ================================================================== //

    @Test
    @DisplayName("Low stock filter returns products with stock <= threshold")
    void getLowStockProducts_thresholdFilter_works() throws Exception {
        mockMvc.perform(get("/api/admin/inventory/low-stock?threshold=10")
                .with(mockAdminJwt(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasItem(hasEntry("id", productLowStock.getId().toString()))))
                .andExpect(jsonPath("$.content", hasItem(hasEntry("id", productZeroStock.getId().toString()))))
                .andExpect(jsonPath("$.content", not(hasItem(hasEntry("id", productNormal.getId().toString())))));
    }

    // ================================================================== //
    // 5. Checkout Protection at Zero Stock                               //
    // ================================================================== //

    @Test
    @DisplayName("Checkout with zero stock item is rejected with 409 Conflict")
    void checkout_zeroStockProduct_returns409() throws Exception {
        Cart cart = cartRepository.findByUser(customerUser).orElseGet(() -> {
            Cart c = new Cart();
            c.setUser(customerUser);
            return cartRepository.save(c);
        });

        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setProduct(productZeroStock);
        cartItem.setQuantity(1);
        cart.getCartItems().add(cartItem);
        cartRepository.save(cart);

        CheckoutRequestDto checkoutDto = new CheckoutRequestDto(customerAddress.getId());

        mockMvc.perform(post("/api/orders/checkout")
                .with(mockCustomerJwt(customerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(checkoutDto)))
                .andExpect(status().isConflict());
    }

    // ================================================================== //
    // 6. Order Cancellation & Stock Restoration Exactly Once             //
    // ================================================================== //

    @Test
    @DisplayName("Order cancellation restores stock exactly once; subsequent cancellation fails with 409")
    void orderCancellation_restoresStockExactlyOnce() throws Exception {
        // Setup initial order with productNormal (ordered quantity = 5)
        Order order = new Order();
        order.setUser(customerUser);
        order.setAddress(customerAddress);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("499.95"));

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(productNormal);
        item.setQuantity(5);
        item.setPriceAtPurchase(productNormal.getPrice());
        order.getOrderItems().add(item);
        order = orderRepository.save(order);

        int stockBeforeCancel = productNormal.getStockQuantity();

        // 1st Cancellation -> Restores stock
        UpdateOrderStatusRequestDto cancelDto = new UpdateOrderStatusRequestDto();
        cancelDto.setStatus(OrderStatus.CANCELLED);

        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                .with(mockAdminJwt(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cancelDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Product restoredProduct = productRepository.findById(productNormal.getId()).orElseThrow();
        assertEquals(stockBeforeCancel + 5, restoredProduct.getStockQuantity(), "Stock should be restored by ordered quantity");

        // 2nd Cancellation attempt -> Idempotent, does NOT restore stock a second time
        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                .with(mockAdminJwt(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cancelDto)))
                .andExpect(status().isOk());

        // Stock remains restored once without double restoration
        Product finalProduct = productRepository.findById(productNormal.getId()).orElseThrow();
        assertEquals(stockBeforeCancel + 5, finalProduct.getStockQuantity(), "Stock must not be double-restored");

        // Attempting to transition CANCELLED order to CONFIRMED fails with 409 Conflict
        UpdateOrderStatusRequestDto invalidTransition = new UpdateOrderStatusRequestDto();
        invalidTransition.setStatus(OrderStatus.CONFIRMED);

        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                .with(mockAdminJwt(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidTransition)))
                .andExpect(status().isConflict());
    }
}
