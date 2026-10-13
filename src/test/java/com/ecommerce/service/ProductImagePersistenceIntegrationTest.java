package com.ecommerce.service;

import com.ecommerce.config.CatalogDataPopulationRunner;
import com.ecommerce.dto.ProductRequestDto;
import com.ecommerce.dto.ProductResponseDto;
import com.ecommerce.entity.Product;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ProductImagePersistenceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CatalogDataPopulationRunner runner;

    private static final UUID TEST_PRODUCT_ID = UUID.fromString("5f31611b-cf1d-4630-bbc5-407878c6cf18");
    private static final String NEW_VALID_IMAGE_URL =
            "https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?auto=format&fit=crop&w=600&q=80";

    @Test
    @DisplayName("Prove image URL persistence end-to-end on one product across simulated server restarts")
    void testSingleProductImagePersistenceEndToEnd() throws Exception {
        // Step 1: Fetch initial product state
        ProductResponseDto initialProduct = productService.getProductById(TEST_PRODUCT_ID);
        assertNotNull(initialProduct, "Target product must exist in database");
        String originalImageUrl = initialProduct.getImageUrl();
        assertNotNull(originalImageUrl, "Original image URL should be present");

        // Step 2: Update product with the new valid image URL
        ProductRequestDto updateRequest = ProductRequestDto.builder()
                .name(initialProduct.getName())
                .description(initialProduct.getDescription())
                .price(initialProduct.getPrice())
                .stockQuantity(initialProduct.getStockQuantity())
                .imageUrl(NEW_VALID_IMAGE_URL)
                .categoryId(initialProduct.getCategory().getId())
                .enabled(initialProduct.isEnabled())
                .build();

        ProductResponseDto updateResponse = productService.updateProduct(TEST_PRODUCT_ID, updateRequest);

        // Step 3: Verify the update response returns the new URL
        assertEquals(NEW_VALID_IMAGE_URL, updateResponse.getImageUrl(),
                "Update response must return the new image URL");

        // Step 4: Fresh API fetch
        ProductResponseDto freshFetch = productService.getProductById(TEST_PRODUCT_ID);
        assertEquals(NEW_VALID_IMAGE_URL, freshFetch.getImageUrl(),
                "Fresh API fetch must return the newly saved image URL");

        // Step 5: Direct repository fetch from database
        Product dbProduct = productRepository.findById(TEST_PRODUCT_ID).orElseThrow();
        assertEquals(NEW_VALID_IMAGE_URL, dbProduct.getImageUrl(),
                "Database entity column must persist the new image URL");

        // Step 6: Simulate Spring Boot restart (CatalogDataPopulationRunner execution)
        runner.run(new String[0]);

        // Step 7: Verify runner did NOT overwrite the admin-saved URL
        ProductResponseDto postRunnerFetch = productService.getProductById(TEST_PRODUCT_ID);
        assertEquals(NEW_VALID_IMAGE_URL, postRunnerFetch.getImageUrl(),
                "Runner restart must NOT overwrite the admin-saved image URL!");

        // Step 8: Edit a different field (description) and verify image URL remains unchanged
        String updatedDescription = "Ergonomic high-precision laser mouse with quiet clicks and MagSpeed scroll.";
        ProductRequestDto secondaryUpdateRequest = ProductRequestDto.builder()
                .name(initialProduct.getName())
                .description(updatedDescription)
                .price(initialProduct.getPrice())
                .stockQuantity(initialProduct.getStockQuantity())
                .imageUrl(NEW_VALID_IMAGE_URL)
                .categoryId(initialProduct.getCategory().getId())
                .enabled(initialProduct.isEnabled())
                .build();

        ProductResponseDto secondaryResponse = productService.updateProduct(TEST_PRODUCT_ID, secondaryUpdateRequest);
        assertEquals(updatedDescription, secondaryResponse.getDescription());
        assertEquals(NEW_VALID_IMAGE_URL, secondaryResponse.getImageUrl(),
                "Image URL must remain intact after editing description");

        // Verify fresh fetch
        ProductResponseDto finalFetch = productService.getProductById(TEST_PRODUCT_ID);
        assertEquals(updatedDescription, finalFetch.getDescription());
        assertEquals(NEW_VALID_IMAGE_URL, finalFetch.getImageUrl(),
                "Final fresh fetch must still have the new image URL");

        // Step 9: Test product creation with image URL
        ProductRequestDto createDto = ProductRequestDto.builder()
                .name("Test Brand New Ergonomic Keyboard")
                .description("Mechanical keyboard for verification test")
                .price(new BigDecimal("3499.00"))
                .stockQuantity(25)
                .imageUrl("https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=600&q=80")
                .categoryId(initialProduct.getCategory().getId())
                .enabled(true)
                .build();

        ProductResponseDto created = productService.createProduct(createDto);
        assertNotNull(created.getId());
        assertEquals(createDto.getImageUrl(), created.getImageUrl());

        // Verify fresh fetch of created product
        ProductResponseDto fetchedCreated = productService.getProductById(created.getId());
        assertEquals(createDto.getImageUrl(), fetchedCreated.getImageUrl());

        // Clean up created test product to keep catalog at clean state
        productRepository.deleteById(created.getId());
    }

    @Test
    @DisplayName("Update and verify the 6 approved products with replacement URLs, preserving all other fields")
    void testUpdateSixApprovedProducts() {
        record TargetUpdate(UUID id, String expectedName, String newImageUrl) {}

        List<TargetUpdate> updates = List.of(
                new TargetUpdate(
                        UUID.fromString("6415a60a-f165-496d-92ed-d1716641dbfc"),
                        "Tata Sampann Unpolished Toor Dal (1kg)",
                        "https://images.unsplash.com/photo-1515543237350-b3eea1ec8082?auto=format&fit=crop&w=600&q=80"
                ),
                new TargetUpdate(
                        UUID.fromString("d62ee8ef-3ed2-4ebd-ae91-c8891fb8e32d"),
                        "Saffola Masala Oats Classic Masala (1kg)",
                        "https://images.unsplash.com/photo-1517673132405-a56a62b18caf?auto=format&fit=crop&w=600&q=80"
                ),
                new TargetUpdate(
                        UUID.fromString("2034f534-16f8-4557-ad99-12b27b275002"),
                        "Levi's Men's 511 Slim Fit Stretch Denim Jeans",
                        "https://images.unsplash.com/photo-1541099649105-f69ad21f3246?auto=format&fit=crop&w=600&q=80"
                ),
                new TargetUpdate(
                        UUID.fromString("a6cad705-9d74-415f-903f-64472732b955"),
                        "Hawkins Contura Hard Anodised Inner Lid Pressure Cooker (3L)",
                        "https://images.unsplash.com/photo-1584269600464-37b1b58a9fe7?auto=format&fit=crop&w=600&q=80"
                ),
                new TargetUpdate(
                        UUID.fromString("7924291b-a95d-465c-8971-a611c7bedff1"),
                        "Plum Green Tea Pore Cleansing Face Wash (120ml)",
                        "https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=600&q=80"
                ),
                new TargetUpdate(
                        UUID.fromString("fb8e820e-471e-4111-805a-23b08807f751"),
                        "Forest Essentials Delicate Facial Cleanser Kashmiri Saffron (200ml)",
                        "https://images.unsplash.com/photo-1601049541289-9b1b7bbbfe19?auto=format&fit=crop&w=600&q=80"
                )
        );

        for (TargetUpdate target : updates) {
            // 1. Fetch current product
            ProductResponseDto current = productService.getProductById(target.id());
            assertEquals(target.expectedName(), current.getName(), "Product name must match expected target");

            // 2. Prepare update DTO, preserving 100% of price, stock, description, category, and enabled
            ProductRequestDto updateDto = ProductRequestDto.builder()
                    .name(current.getName())
                    .description(current.getDescription())
                    .price(current.getPrice())
                    .stockQuantity(current.getStockQuantity())
                    .imageUrl(target.newImageUrl())
                    .categoryId(current.getCategory().getId())
                    .enabled(current.isEnabled())
                    .build();

            // 3. Update via backend ProductService
            ProductResponseDto updated = productService.updateProduct(target.id(), updateDto);
            assertEquals(target.newImageUrl(), updated.getImageUrl(), "Update response must return new image URL");

            // 4. Fetch fresh and verify persistence and preservation of other fields
            ProductResponseDto fresh = productService.getProductById(target.id());
            assertEquals(target.newImageUrl(), fresh.getImageUrl(), "Fresh GET must return new image URL");
            assertEquals(current.getName(), fresh.getName());
            assertEquals(current.getDescription(), fresh.getDescription());
            assertEquals(0, current.getPrice().compareTo(fresh.getPrice()));
            assertEquals(current.getStockQuantity(), fresh.getStockQuantity());
            assertEquals(current.getCategory().getId(), fresh.getCategory().getId());
            assertEquals(current.isEnabled(), fresh.isEnabled());
        }

        // Verify total product count remains exactly 60
        long count = productRepository.findAll().stream().filter(Product::isEnabled).count();
        assertEquals(60, count, "Active product count must remain exactly 60");
    }
}
