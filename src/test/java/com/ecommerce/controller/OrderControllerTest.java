package com.ecommerce.controller;

import com.ecommerce.dto.CheckoutRequestDto;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Order & Checkout module.
 *
 * Each test runs inside a transaction that is rolled back after the test,
 * so no persistent side-effects leak between tests.
 *
 * All JWT identities are mocked using Spring Security Test's jwt() post-processor.
 * The subject UUID is set to the test user's actual UUID so that
 * SecurityUtils.getCurrentUserId() returns the correct identity.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerTest {

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
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    // ------------------------------------------------------------------ //
    //  Test fixtures                                                       //
    // ------------------------------------------------------------------ //

    private User userA;
    private User userB;
    private Category category;
    private Product productA;   // normal stock
    private Product productB;   // low stock (1 unit)
    private Product disabledProduct;
    private Address addressA;   // belongs to userA
    private Address addressB;   // belongs to userB

    /** Creates a mock JWT post-processor for the given user. */
    private JwtRequestPostProcessor mockJwt(User user) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(builder -> builder
                        .subject(user.getId().toString())
                        .claim("email", user.getEmail()));
    }

    @BeforeEach
    void setUp() {
        // Users
        userA = new User();
        userA.setId(UUID.randomUUID());
        userA.setEmail("user-a@example.com");
        userA.setFullName("User A");
        userA.setRole(UserRole.ROLE_USER);
        userA.setEnabled(true);
        userRepository.save(userA);

        userB = new User();
        userB.setId(UUID.randomUUID());
        userB.setEmail("user-b@example.com");
        userB.setFullName("User B");
        userB.setRole(UserRole.ROLE_USER);
        userB.setEnabled(true);
        userRepository.save(userB);

        // Category
        category = new Category();
        category.setName("Order Cat " + UUID.randomUUID());
        categoryRepository.save(category);

        // Products
        productA = new Product();
        productA.setName("Laptop Pro");
        productA.setPrice(new BigDecimal("1299.99"));
        productA.setStockQuantity(50);
        productA.setCategory(category);
        productA.setEnabled(true);
        productRepository.save(productA);

        productB = new Product();
        productB.setName("Last Item Widget");
        productB.setPrice(new BigDecimal("9.99"));
        productB.setStockQuantity(1);
        productB.setCategory(category);
        productB.setEnabled(true);
        productRepository.save(productB);

        disabledProduct = new Product();
        disabledProduct.setName("Discontinued Item");
        disabledProduct.setPrice(new BigDecimal("49.99"));
        disabledProduct.setStockQuantity(10);
        disabledProduct.setCategory(category);
        disabledProduct.setEnabled(false);
        productRepository.save(disabledProduct);

        // Addresses
        addressA = new Address();
        addressA.setUser(userA);
        addressA.setStreet("123 Main St");
        addressA.setCity("Springfield");
        addressA.setState("IL");
        addressA.setZip("62701");
        addressA.setCountry("US");
        addressRepository.save(addressA);

        addressB = new Address();
        addressB.setUser(userB);
        addressB.setStreet("456 Oak Ave");
        addressB.setCity("Shelbyville");
        addressB.setState("IL");
        addressB.setZip("62565");
        addressB.setCountry("US");
        addressRepository.save(addressB);
    }

    // ------------------------------------------------------------------ //
    //  Helper: seed userA's cart with the given product and quantity       //
    // ------------------------------------------------------------------ //

    private void addToUserACart(Product product, int qty) {
        Cart cart = cartRepository.findByUserId(userA.getId()).orElseGet(() -> {
            Cart c = new Cart();
            c.setUser(userA);
            return cartRepository.save(c);
        });

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(qty);
        cart.getCartItems().add(item);
        cartRepository.save(cart);
    }

    // ------------------------------------------------------------------ //
    //  Authentication guard tests                                          //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Authentication guard")
    class AuthGuard {

        @Test
        @DisplayName("POST /api/orders/checkout → 401 without JWT")
        void checkout_unauthenticated_returns401() throws Exception {
            CheckoutRequestDto body = new CheckoutRequestDto(addressA.getId());

            mockMvc.perform(post("/api/orders/checkout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/orders → 401 without JWT")
        void listOrders_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/orders/{id} → 401 without JWT")
        void getOrder_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/orders/" + UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ //
    //  Checkout — happy path                                               //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Checkout — happy path")
    class CheckoutHappyPath {

        @Test
        @DisplayName("Single product → 201 with correct order structure")
        void checkout_singleProduct_returns201() throws Exception {
            addToUserACart(productA, 2);

            CheckoutRequestDto body = new CheckoutRequestDto(addressA.getId());

            mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isString())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.totalAmount").value(closeTo(2599.98, 0.01)))
                    .andExpect(jsonPath("$.address.id").value(addressA.getId().toString()))
                    .andExpect(jsonPath("$.address.street").value("123 Main St"))
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].productId").value(productA.getId().toString()))
                    .andExpect(jsonPath("$.items[0].quantity").value(2))
                    .andExpect(jsonPath("$.items[0].priceAtPurchase").value(closeTo(1299.99, 0.01)))
                    .andExpect(jsonPath("$.items[0].lineTotal").value(closeTo(2599.98, 0.01)));
        }

        @Test
        @DisplayName("Multiple products → totalAmount is sum of all line totals")
        void checkout_multipleProducts_correctTotal() throws Exception {
            addToUserACart(productA, 1);  // 1299.99
            addToUserACart(productB, 1);  //    9.99

            CheckoutRequestDto body = new CheckoutRequestDto(addressA.getId());

            mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.totalAmount").value(closeTo(1309.98, 0.01)))
                    .andExpect(jsonPath("$.items", hasSize(2)));
        }

        @Test
        @DisplayName("Cart is cleared after successful checkout")
        void checkout_clearsCart() throws Exception {
            addToUserACart(productA, 1);

            CheckoutRequestDto body = new CheckoutRequestDto(addressA.getId());

            mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());

            // Cart should now be empty
            mockMvc.perform(get("/api/cart")
                    .with(mockJwt(userA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itemCount").value(0));
        }

        @Test
        @DisplayName("Stock is decremented after successful checkout")
        void checkout_decrementsStock() throws Exception {
            int initialStock = productA.getStockQuantity(); // 50
            addToUserACart(productA, 3);

            CheckoutRequestDto body = new CheckoutRequestDto(addressA.getId());

            mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());

            Product updated = productRepository.findById(productA.getId()).orElseThrow();
            assert updated.getStockQuantity() == (initialStock - 3);
        }
    }

    // ------------------------------------------------------------------ //
    //  Checkout — validation failures                                      //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Checkout — validation failures")
    class CheckoutValidation {

        @Test
        @DisplayName("Empty cart → 409 Conflict")
        void checkout_emptyCart_returns409() throws Exception {
            // No items added to cart
            CheckoutRequestDto body = new CheckoutRequestDto(addressA.getId());

            mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Missing addressId → 400 Bad Request")
        void checkout_missingAddressId_returns400() throws Exception {
            addToUserACart(productA, 1);

            // addressId is null
            CheckoutRequestDto body = new CheckoutRequestDto(null);

            mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Non-existent address → 404 Not Found")
        void checkout_unknownAddress_returns404() throws Exception {
            addToUserACart(productA, 1);
            CheckoutRequestDto body = new CheckoutRequestDto(UUID.randomUUID());

            mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Address belonging to another user → 404 Not Found (ownership isolation)")
        void checkout_anotherUsersAddress_returns404() throws Exception {
            addToUserACart(productA, 1);
            // addressB belongs to userB — userA must not be able to use it
            CheckoutRequestDto body = new CheckoutRequestDto(addressB.getId());

            mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Disabled product in cart → 409 Conflict")
        void checkout_disabledProduct_returns409() throws Exception {
            addToUserACart(disabledProduct, 1);
            CheckoutRequestDto body = new CheckoutRequestDto(addressA.getId());

            mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Insufficient stock → 409 Conflict")
        void checkout_insufficientStock_returns409() throws Exception {
            // productB has stockQuantity = 1, requesting 2
            addToUserACart(productB, 1);
            // Manually set stock to 0 to simulate race condition after item was added to cart
            productB.setStockQuantity(0);
            productRepository.save(productB);

            CheckoutRequestDto body = new CheckoutRequestDto(addressA.getId());

            mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isConflict());
        }
    }

    // ------------------------------------------------------------------ //
    //  Order history — GET /api/orders                                     //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Order history — GET /api/orders")
    class OrderHistory {

        @Test
        @DisplayName("Empty history → 200 with empty page")
        void getOrders_noOrders_returnsEmptyPage() throws Exception {
            mockMvc.perform(get("/api/orders")
                    .with(mockJwt(userA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("After checkout → order appears in history")
        void getOrders_afterCheckout_returnsOrder() throws Exception {
            addToUserACart(productA, 1);
            CheckoutRequestDto body = new CheckoutRequestDto(addressA.getId());

            mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/orders")
                    .with(mockJwt(userA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("User isolation — userA cannot see userB's orders")
        void getOrders_userIsolation() throws Exception {
            // userA places an order
            addToUserACart(productA, 1);
            mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CheckoutRequestDto(addressA.getId()))))
                    .andExpect(status().isCreated());

            // userB's history should be empty
            mockMvc.perform(get("/api/orders")
                    .with(mockJwt(userB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Pagination — size param is respected")
        void getOrders_pagination_sizeRespected() throws Exception {
            mockMvc.perform(get("/api/orders")
                    .param("page", "0")
                    .param("size", "5")
                    .with(mockJwt(userA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(5));
        }
    }

    // ------------------------------------------------------------------ //
    //  Order detail — GET /api/orders/{orderId}                           //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Order detail — GET /api/orders/{orderId}")
    class OrderDetail {

        @Test
        @DisplayName("Owner can fetch their order → 200")
        void getOrder_owner_returns200() throws Exception {
            addToUserACart(productA, 1);
            String responseBody = mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CheckoutRequestDto(addressA.getId()))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String orderId = objectMapper.readTree(responseBody).get("id").asText();

            mockMvc.perform(get("/api/orders/" + orderId)
                    .with(mockJwt(userA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(orderId))
                    .andExpect(jsonPath("$.items", hasSize(1)));
        }

        @Test
        @DisplayName("Non-existent order ID → 404")
        void getOrder_unknownId_returns404() throws Exception {
            mockMvc.perform(get("/api/orders/" + UUID.randomUUID())
                    .with(mockJwt(userA)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Different user's order ID → 404 (ownership isolation, no 403 leak)")
        void getOrder_otherUsersOrder_returns404() throws Exception {
            // userA places an order
            addToUserACart(productA, 1);
            String responseBody = mockMvc.perform(post("/api/orders/checkout")
                    .with(mockJwt(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CheckoutRequestDto(addressA.getId()))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String userAOrderId = objectMapper.readTree(responseBody).get("id").asText();

            // userB tries to fetch userA's order — must get 404, not 403
            mockMvc.perform(get("/api/orders/" + userAOrderId)
                    .with(mockJwt(userB)))
                    .andExpect(status().isNotFound());
        }
    }
}
