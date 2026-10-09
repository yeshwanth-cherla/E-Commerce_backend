package com.ecommerce.controller;

import com.ecommerce.dto.AddCartItemRequestDto;
import com.ecommerce.dto.UpdateCartItemRequestDto;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserRole;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CartControllerTest {

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
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private User userA;
    private User userB;
    private Category category;
    private Product enabledProduct;
    private Product disabledProduct;
    private Product lowStockProduct;

    private JwtRequestPostProcessor mockJwt(User user) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(builder -> builder.subject(user.getId().toString()).claim("email", user.getEmail()));
    }

    @BeforeEach
    void setUp() {
        // Create test users with explicitly set UUIDs
        userA = new User();
        userA.setId(UUID.randomUUID());
        userA.setEmail("usera_" + UUID.randomUUID() + "@example.com");
        userA.setFullName("User A");
        userA.setRole(UserRole.ROLE_USER);
        userA.setEnabled(true);
        userA = userRepository.save(userA);

        userB = new User();
        userB.setId(UUID.randomUUID());
        userB.setEmail("userb_" + UUID.randomUUID() + "@example.com");
        userB.setFullName("User B");
        userB.setRole(UserRole.ROLE_USER);
        userB.setEnabled(true);
        userB = userRepository.save(userB);

        // Create test category
        category = new Category();
        category.setName("Cart Test Category " + UUID.randomUUID());
        category.setDescription("Test Category Description");
        category = categoryRepository.save(category);

        // Create enabled product
        enabledProduct = new Product();
        enabledProduct.setName("Cart Enabled Product");
        enabledProduct.setDescription("A valid product");
        enabledProduct.setPrice(new BigDecimal("99.99"));
        enabledProduct.setStockQuantity(20);
        enabledProduct.setCategory(category);
        enabledProduct.setEnabled(true);
        enabledProduct = productRepository.save(enabledProduct);

        // Create disabled product
        disabledProduct = new Product();
        disabledProduct.setName("Cart Disabled Product");
        disabledProduct.setDescription("A disabled product");
        disabledProduct.setPrice(new BigDecimal("49.99"));
        disabledProduct.setStockQuantity(10);
        disabledProduct.setCategory(category);
        disabledProduct.setEnabled(false);
        disabledProduct = productRepository.save(disabledProduct);

        // Create low stock product
        lowStockProduct = new Product();
        lowStockProduct.setName("Cart Low Stock Product");
        lowStockProduct.setDescription("Only 3 left");
        lowStockProduct.setPrice(new BigDecimal("10.00"));
        lowStockProduct.setStockQuantity(3);
        lowStockProduct.setCategory(category);
        lowStockProduct.setEnabled(true);
        lowStockProduct = productRepository.save(lowStockProduct);
    }

    @Nested
    @DisplayName("AUTHENTICATION TESTS")
    class AuthenticationTests {

        @Test
        @DisplayName("21. Unauthenticated GET /api/cart -> 401")
        void getCart_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/cart"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("22. Unauthenticated POST /api/cart/items -> 401")
        void addCartItem_Unauthenticated_Returns401() throws Exception {
            AddCartItemRequestDto dto = AddCartItemRequestDto.builder()
                    .productId(enabledProduct.getId())
                    .quantity(1)
                    .build();

            mockMvc.perform(post("/api/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("23. Unauthenticated PUT /api/cart/items/{id} -> 401")
        void updateCartItem_Unauthenticated_Returns401() throws Exception {
            UpdateCartItemRequestDto dto = UpdateCartItemRequestDto.builder().quantity(2).build();

            mockMvc.perform(put("/api/cart/items/" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("24. Unauthenticated DELETE -> 401")
        void deleteCartItem_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(delete("/api/cart/items/" + UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(delete("/api/cart"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("BASIC CART OPERATIONS")
    class BasicCartOperations {

        @Test
        @DisplayName("1. Authenticated user gets empty cart; 2. Cart created when needed")
        void getCart_NewUser_CreatesAndReturnsEmptyCart() throws Exception {
            mockMvc.perform(get("/api/cart").with(mockJwt(userA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.items", hasSize(0)))
                    .andExpect(jsonPath("$.itemCount").value(0))
                    .andExpect(jsonPath("$.cartTotal").value(0));
        }

        @Test
        @DisplayName("3. Repeated GET does not create duplicate carts")
        void getCart_RepeatedCalls_ReturnsSameCart() throws Exception {
            mockMvc.perform(get("/api/cart").with(mockJwt(userA)))
                    .andExpect(status().isOk());

            long cartCount = cartRepository.count();

            mockMvc.perform(get("/api/cart").with(mockJwt(userA)))
                    .andExpect(status().isOk());

            assertEquals(cartCount, cartRepository.count());
        }

        @Test
        @DisplayName("4. Add product to cart; 9. Line total; 10. Cart total; 11. Item count")
        void addCartItem_ValidProduct_AddsItemSuccessfully() throws Exception {
            AddCartItemRequestDto dto = AddCartItemRequestDto.builder()
                    .productId(enabledProduct.getId())
                    .quantity(2)
                    .build();

            mockMvc.perform(post("/api/cart/items")
                            .with(mockJwt(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].productId").value(enabledProduct.getId().toString()))
                    .andExpect(jsonPath("$.items[0].quantity").value(2))
                    .andExpect(jsonPath("$.items[0].productPrice").value(99.99))
                    .andExpect(jsonPath("$.items[0].lineTotal").value(199.98))
                    .andExpect(jsonPath("$.itemCount").value(2))
                    .andExpect(jsonPath("$.cartTotal").value(199.98));
        }

        @Test
        @DisplayName("5. Add same product again increases quantity")
        void addCartItem_SameProductTwice_IncreasesQuantity() throws Exception {
            AddCartItemRequestDto dto1 = AddCartItemRequestDto.builder()
                    .productId(enabledProduct.getId())
                    .quantity(2)
                    .build();

            mockMvc.perform(post("/api/cart/items")
                            .with(mockJwt(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto1)))
                    .andExpect(status().isCreated());

            AddCartItemRequestDto dto2 = AddCartItemRequestDto.builder()
                    .productId(enabledProduct.getId())
                    .quantity(3)
                    .build();

            mockMvc.perform(post("/api/cart/items")
                            .with(mockJwt(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto2)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].quantity").value(5))
                    .andExpect(jsonPath("$.itemCount").value(5));
        }

        @Test
        @DisplayName("6. Update cart item quantity")
        void updateCartItemQuantity_ValidQuantity_UpdatesItem() throws Exception {
            AddCartItemRequestDto addDto = AddCartItemRequestDto.builder()
                    .productId(enabledProduct.getId())
                    .quantity(2)
                    .build();

            String responseString = mockMvc.perform(post("/api/cart/items")
                            .with(mockJwt(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addDto)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String cartItemId = objectMapper.readTree(responseString).get("items").get(0).get("id").asText();

            UpdateCartItemRequestDto updateDto = UpdateCartItemRequestDto.builder().quantity(4).build();

            mockMvc.perform(put("/api/cart/items/" + cartItemId)
                            .with(mockJwt(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].quantity").value(4))
                    .andExpect(jsonPath("$.itemCount").value(4));
        }

        @Test
        @DisplayName("7. Remove cart item")
        void removeCartItem_ExistingItem_RemovesItem() throws Exception {
            AddCartItemRequestDto addDto = AddCartItemRequestDto.builder()
                    .productId(enabledProduct.getId())
                    .quantity(2)
                    .build();

            String responseString = mockMvc.perform(post("/api/cart/items")
                            .with(mockJwt(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addDto)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String cartItemId = objectMapper.readTree(responseString).get("items").get(0).get("id").asText();

            mockMvc.perform(delete("/api/cart/items/" + cartItemId)
                            .with(mockJwt(userA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)))
                    .andExpect(jsonPath("$.itemCount").value(0));
        }

        @Test
        @DisplayName("8. Clear cart")
        void clearCart_CartWithItems_ClearsAllItems() throws Exception {
            AddCartItemRequestDto addDto1 = AddCartItemRequestDto.builder().productId(enabledProduct.getId()).quantity(2).build();
            AddCartItemRequestDto addDto2 = AddCartItemRequestDto.builder().productId(lowStockProduct.getId()).quantity(1).build();

            mockMvc.perform(post("/api/cart/items").with(mockJwt(userA)).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addDto1))).andExpect(status().isCreated());
            mockMvc.perform(post("/api/cart/items").with(mockJwt(userA)).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addDto2))).andExpect(status().isCreated());

            mockMvc.perform(delete("/api/cart").with(mockJwt(userA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)))
                    .andExpect(jsonPath("$.itemCount").value(0))
                    .andExpect(jsonPath("$.cartTotal").value(0));
        }
    }

    @Nested
    @DisplayName("VALIDATION TESTS")
    class ValidationTests {

        @Test
        @DisplayName("12. Quantity zero rejected")
        void addCartItem_QuantityZero_Returns400() throws Exception {
            AddCartItemRequestDto dto = AddCartItemRequestDto.builder()
                    .productId(enabledProduct.getId())
                    .quantity(0)
                    .build();

            mockMvc.perform(post("/api/cart/items")
                            .with(mockJwt(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("13. Negative quantity rejected")
        void addCartItem_NegativeQuantity_Returns400() throws Exception {
            AddCartItemRequestDto dto = AddCartItemRequestDto.builder()
                    .productId(enabledProduct.getId())
                    .quantity(-2)
                    .build();

            mockMvc.perform(post("/api/cart/items")
                            .with(mockJwt(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("14. Missing product rejected")
        void addCartItem_NonExistentProduct_Returns404() throws Exception {
            AddCartItemRequestDto dto = AddCartItemRequestDto.builder()
                    .productId(UUID.randomUUID())
                    .quantity(1)
                    .build();

            mockMvc.perform(post("/api/cart/items")
                            .with(mockJwt(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("15 & 27. Disabled product cannot be added")
        void addCartItem_DisabledProduct_Returns409() throws Exception {
            AddCartItemRequestDto dto = AddCartItemRequestDto.builder()
                    .productId(disabledProduct.getId())
                    .quantity(1)
                    .build();

            mockMvc.perform(post("/api/cart/items")
                            .with(mockJwt(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("16. Quantity greater than stock rejected")
        void addCartItem_QuantityExceedsStock_Returns409() throws Exception {
            AddCartItemRequestDto dto = AddCartItemRequestDto.builder()
                    .productId(lowStockProduct.getId())
                    .quantity(5) // Only 3 in stock
                    .build();

            mockMvc.perform(post("/api/cart/items")
                            .with(mockJwt(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("OWNERSHIP & SECURITY TESTS")
    class OwnershipTests {

        @Test
        @DisplayName("17. User A cannot read User B's cart & 25, 26. Ownership derived from SecurityContext")
        void getCart_UserA_CannotSeeUserBCart() throws Exception {
            // User A adds item
            AddCartItemRequestDto dto = AddCartItemRequestDto.builder().productId(enabledProduct.getId()).quantity(2).build();
            mockMvc.perform(post("/api/cart/items").with(mockJwt(userA)).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andExpect(status().isCreated());

            // User B requests cart -> gets User B's empty cart
            mockMvc.perform(get("/api/cart").with(mockJwt(userB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)));
        }

        @Test
        @DisplayName("18. User B cannot modify User A's cart item -> 404")
        void updateCartItem_OtherUserItem_Returns404() throws Exception {
            AddCartItemRequestDto addDto = AddCartItemRequestDto.builder().productId(enabledProduct.getId()).quantity(2).build();
            String responseString = mockMvc.perform(post("/api/cart/items")
                            .with(mockJwt(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addDto)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String userACartItemId = objectMapper.readTree(responseString).get("items").get(0).get("id").asText();

            UpdateCartItemRequestDto updateDto = UpdateCartItemRequestDto.builder().quantity(5).build();

            // User B tries to update User A's cart item
            mockMvc.perform(put("/api/cart/items/" + userACartItemId)
                            .with(mockJwt(userB))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("19. User B cannot delete User A's cart item -> 404")
        void deleteCartItem_OtherUserItem_Returns404() throws Exception {
            AddCartItemRequestDto addDto = AddCartItemRequestDto.builder().productId(enabledProduct.getId()).quantity(2).build();
            String responseString = mockMvc.perform(post("/api/cart/items")
                            .with(mockJwt(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addDto)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String userACartItemId = objectMapper.readTree(responseString).get("items").get(0).get("id").asText();

            // User B tries to delete User A's cart item
            mockMvc.perform(delete("/api/cart/items/" + userACartItemId)
                            .with(mockJwt(userB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("20. User B cannot clear User A's cart")
        void clearCart_UserB_DoesNotClearUserACart() throws Exception {
            AddCartItemRequestDto addDto = AddCartItemRequestDto.builder().productId(enabledProduct.getId()).quantity(2).build();
            mockMvc.perform(post("/api/cart/items").with(mockJwt(userA)).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addDto))).andExpect(status().isCreated());

            // User B clears cart (clears B's cart)
            mockMvc.perform(delete("/api/cart").with(mockJwt(userB)))
                    .andExpect(status().isOk());

            // User A's cart still has item
            mockMvc.perform(get("/api/cart").with(mockJwt(userA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(1)));
        }
    }
}
