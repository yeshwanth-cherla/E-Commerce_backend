package com.ecommerce.controller;

import com.ecommerce.dto.ReviewRequestDto;
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
 * Integration tests for Product Reviews module (Stage 10).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewControllerTest {

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
    private ReviewRepository reviewRepository;

    private User userA;
    private User userB;
    private Category categoryTech;
    private Product productActive;
    private Product productDisabled;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setId(UUID.randomUUID());
        userA.setEmail("usera-rev-" + UUID.randomUUID() + "@example.com");
        userA.setFullName("User Alpha");
        userA.setRole(UserRole.ROLE_USER);
        userA.setEnabled(true);
        userA = userRepository.save(userA);

        userB = new User();
        userB.setId(UUID.randomUUID());
        userB.setEmail("userb-rev-" + UUID.randomUUID() + "@example.com");
        userB.setFullName("User Beta");
        userB.setRole(UserRole.ROLE_USER);
        userB.setEnabled(true);
        userB = userRepository.save(userB);

        categoryTech = new Category();
        categoryTech.setName("Review Category " + UUID.randomUUID());
        categoryTech.setDescription("Category for review testing");
        categoryTech = categoryRepository.save(categoryTech);

        productActive = new Product();
        productActive.setName("Active Laptop " + UUID.randomUUID());
        productActive.setDescription("Great laptop");
        productActive.setPrice(new BigDecimal("1200.00"));
        productActive.setStockQuantity(10);
        productActive.setCategory(categoryTech);
        productActive.setEnabled(true);
        productActive = productRepository.save(productActive);

        productDisabled = new Product();
        productDisabled.setName("Disabled Phone " + UUID.randomUUID());
        productDisabled.setDescription("Disabled phone");
        productDisabled.setPrice(new BigDecimal("500.00"));
        productDisabled.setStockQuantity(0);
        productDisabled.setCategory(categoryTech);
        productDisabled.setEnabled(false);
        productDisabled = productRepository.save(productDisabled);
    }

    private JwtRequestPostProcessor jwtUserA() {
        return jwt()
                .jwt(builder -> builder.subject(userA.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private JwtRequestPostProcessor jwtUserB() {
        return jwt()
                .jwt(builder -> builder.subject(userB.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // ------------------------------------------------------------------ //
    // 1. Unauthenticated & Authorization Tests                           //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("Authentication & Authorization Rules")
    class AuthorizationTests {

        @Test
        @DisplayName("Unauthenticated request to create review returns 401")
        void createReview_Unauthenticated_Returns401() throws Exception {
            ReviewRequestDto dto = ReviewRequestDto.builder()
                    .rating(5)
                    .comment("Unauthenticated review")
                    .build();

            mockMvc.perform(post("/api/products/" + productActive.getId() + "/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Public GET request to list product reviews returns 200 OK")
        void getReviews_Public_Returns200() throws Exception {
            mockMvc.perform(get("/api/products/" + productActive.getId() + "/reviews"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    // ------------------------------------------------------------------ //
    // 2. Review Creation & Duplicate Enforcement                          //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("Review Creation & Aggregate Average Rating")
    class CreationTests {

        @Test
        @DisplayName("Authenticated user creates review successfully and updates product average rating")
        void createReview_Success_UpdatesProductAverageRating() throws Exception {
            ReviewRequestDto dto = ReviewRequestDto.builder()
                    .rating(4)
                    .comment("Very good performance!")
                    .build();

            mockMvc.perform(post("/api/products/" + productActive.getId() + "/reviews")
                            .with(jwtUserA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.productId").value(productActive.getId().toString()))
                    .andExpect(jsonPath("$.rating").value(4))
                    .andExpect(jsonPath("$.comment").value("Very good performance!"))
                    .andExpect(jsonPath("$.userName").value("User Alpha"));

            Product updatedProduct = productRepository.findById(productActive.getId()).orElseThrow();
            assertNotNull(updatedProduct.getAverageRating());
            assertEquals(new BigDecimal("4.00"), updatedProduct.getAverageRating());
        }

        @Test
        @DisplayName("Multiple users review same product — average rating computed correctly")
        void createReviews_MultipleUsers_CalculatesAverage() throws Exception {
            ReviewRequestDto dtoA = ReviewRequestDto.builder().rating(5).comment("Awesome").build();
            mockMvc.perform(post("/api/products/" + productActive.getId() + "/reviews")
                            .with(jwtUserA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dtoA)))
                    .andExpect(status().isCreated());

            ReviewRequestDto dtoB = ReviewRequestDto.builder().rating(2).comment("Not great").build();
            mockMvc.perform(post("/api/products/" + productActive.getId() + "/reviews")
                            .with(jwtUserB())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dtoB)))
                    .andExpect(status().isCreated());

            Product updatedProduct = productRepository.findById(productActive.getId()).orElseThrow();
            // (5 + 2) / 2 = 3.50
            assertEquals(new BigDecimal("3.50"), updatedProduct.getAverageRating());
        }

        @Test
        @DisplayName("Duplicate review by same user returns 409 Conflict")
        void createReview_Duplicate_Returns409Conflict() throws Exception {
            ReviewRequestDto dto = ReviewRequestDto.builder().rating(5).comment("First review").build();
            mockMvc.perform(post("/api/products/" + productActive.getId() + "/reviews")
                            .with(jwtUserA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated());

            ReviewRequestDto duplicateDto = ReviewRequestDto.builder().rating(4).comment("Second attempt").build();
            mockMvc.perform(post("/api/products/" + productActive.getId() + "/reviews")
                            .with(jwtUserA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(duplicateDto)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Reviewing disabled product returns 409 Conflict")
        void createReview_DisabledProduct_Returns409Conflict() throws Exception {
            ReviewRequestDto dto = ReviewRequestDto.builder().rating(5).comment("Nice phone").build();
            mockMvc.perform(post("/api/products/" + productDisabled.getId() + "/reviews")
                            .with(jwtUserA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Invalid rating < 1 or > 5 returns 400 Bad Request")
        void createReview_InvalidRating_Returns400() throws Exception {
            ReviewRequestDto dtoLow = ReviewRequestDto.builder().rating(0).comment("Zero star").build();
            mockMvc.perform(post("/api/products/" + productActive.getId() + "/reviews")
                            .with(jwtUserA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dtoLow)))
                    .andExpect(status().isBadRequest());

            ReviewRequestDto dtoHigh = ReviewRequestDto.builder().rating(6).comment("Six star").build();
            mockMvc.perform(post("/api/products/" + productActive.getId() + "/reviews")
                            .with(jwtUserA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dtoHigh)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------------ //
    // 3. Update & Ownership Isolation                                    //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("Review Modification & Cross-User Ownership Isolation")
    class UpdateAndDeleteTests {

        @Test
        @DisplayName("Owner can update review and update product average rating")
        void updateReview_Owner_Success() throws Exception {
            Review review = new Review();
            review.setUser(userA);
            review.setProduct(productActive);
            review.setRating(2);
            review.setComment("Initial bad review");
            review = reviewRepository.save(review);

            productActive.setAverageRating(new BigDecimal("2.00"));
            productRepository.save(productActive);

            ReviewRequestDto updateDto = ReviewRequestDto.builder()
                    .rating(5)
                    .comment("Changed my mind, product is great!")
                    .build();

            mockMvc.perform(put("/api/reviews/" + review.getId())
                            .with(jwtUserA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rating").value(5))
                    .andExpect(jsonPath("$.comment").value("Changed my mind, product is great!"));

            Product updatedProduct = productRepository.findById(productActive.getId()).orElseThrow();
            assertEquals(new BigDecimal("5.00"), updatedProduct.getAverageRating());
        }

        @Test
        @DisplayName("User B attempting to update User A's review returns 404 Not Found")
        void updateReview_CrossUser_Returns404() throws Exception {
            Review review = new Review();
            review.setUser(userA);
            review.setProduct(productActive);
            review.setRating(4);
            review.setComment("User A's review");
            review = reviewRepository.save(review);

            ReviewRequestDto updateDto = ReviewRequestDto.builder()
                    .rating(1)
                    .comment("User B trying to tamper")
                    .build();

            mockMvc.perform(put("/api/reviews/" + review.getId())
                            .with(jwtUserB())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Owner can delete review and reset average rating when last review deleted")
        void deleteReview_Owner_Success() throws Exception {
            Review review = new Review();
            review.setUser(userA);
            review.setProduct(productActive);
            review.setRating(5);
            review.setComment("Temporary review");
            review = reviewRepository.save(review);

            productActive.setAverageRating(new BigDecimal("5.00"));
            productRepository.save(productActive);

            mockMvc.perform(delete("/api/reviews/" + review.getId()).with(jwtUserA()))
                    .andExpect(status().isNoContent());

            assertFalse(reviewRepository.existsById(review.getId()));
            Product updatedProduct = productRepository.findById(productActive.getId()).orElseThrow();
            assertNull(updatedProduct.getAverageRating());
        }

        @Test
        @DisplayName("User B attempting to delete User A's review returns 404 Not Found")
        void deleteReview_CrossUser_Returns404() throws Exception {
            Review review = new Review();
            review.setUser(userA);
            review.setProduct(productActive);
            review.setRating(5);
            review.setComment("User A's review");
            review = reviewRepository.save(review);

            mockMvc.perform(delete("/api/reviews/" + review.getId()).with(jwtUserB()))
                    .andExpect(status().isNotFound());

            assertTrue(reviewRepository.existsById(review.getId()));
        }
    }
}
