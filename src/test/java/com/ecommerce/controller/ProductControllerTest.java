package com.ecommerce.controller;

import com.ecommerce.dto.ProductRequestDto;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    private Category categoryElectronics;
    private Category categoryClothing;
    private Product productSmartphone;
    private Product productLaptop;

    @BeforeEach
    void setUp() {
        categoryElectronics = new Category();
        categoryElectronics.setName("Electronics Catalogue");
        categoryElectronics.setDescription("Gadgets and Devices");
        categoryElectronics = categoryRepository.save(categoryElectronics);

        categoryClothing = new Category();
        categoryClothing.setName("Clothing Catalogue");
        categoryClothing.setDescription("Apparel and Fashion");
        categoryClothing = categoryRepository.save(categoryClothing);

        productSmartphone = new Product();
        productSmartphone.setName("Flagship Smartphone Pro");
        productSmartphone.setDescription("High-end mobile phone with OLED display");
        productSmartphone.setPrice(new BigDecimal("999.99"));
        productSmartphone.setStockQuantity(50);
        productSmartphone.setImageUrl("https://example.com/phone.jpg");
        productSmartphone.setCategory(categoryElectronics);
        productSmartphone.setEnabled(true);
        productSmartphone = productRepository.save(productSmartphone);

        productLaptop = new Product();
        productLaptop.setName("Ultra Laptop Pro 15");
        productLaptop.setDescription("Lightweight high-performance laptop");
        productLaptop.setPrice(new BigDecimal("1499.50"));
        productLaptop.setStockQuantity(20);
        productLaptop.setImageUrl("https://example.com/laptop.jpg");
        productLaptop.setCategory(categoryElectronics);
        productLaptop.setEnabled(true);
        productLaptop = productRepository.save(productLaptop);
    }

    @Test
    void getProducts_PublicAccess_ReturnsPaginatedProducts() throws Exception {
        mockMvc.perform(get("/api/products?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void getProductById_PublicAccess_ReturnsProductDetails() throws Exception {
        mockMvc.perform(get("/api/products/" + productSmartphone.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productSmartphone.getId().toString()))
                .andExpect(jsonPath("$.name").value("Flagship Smartphone Pro"))
                .andExpect(jsonPath("$.price").value(999.99))
                .andExpect(jsonPath("$.category.name").value("Electronics Catalogue"));
    }

    @Test
    void getProductById_Missing_Returns404NotFound() throws Exception {
        mockMvc.perform(get("/api/products/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void searchProducts_ByTerm_ReturnsMatchingProducts() throws Exception {
        mockMvc.perform(get("/api/products?search=smartphone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Flagship Smartphone Pro"));
    }

    @Test
    void filterProducts_ByCategory_ReturnsCategoryProducts() throws Exception {
        mockMvc.perform(get("/api/products?categoryId=" + categoryElectronics.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/products?categoryId=" + categoryClothing.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void combinedFilter_SearchAndCategory_ReturnsFilteredResults() throws Exception {
        mockMvc.perform(get("/api/products?search=laptop&categoryId=" + categoryElectronics.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Ultra Laptop Pro 15"));
    }

    @Test
    void createProduct_Admin_Returns201Created() throws Exception {
        ProductRequestDto request = ProductRequestDto.builder()
                .name("Wireless Noise Canceling Headphones")
                .description("Over-ear Bluetooth headphones")
                .price(new BigDecimal("199.99"))
                .stockQuantity(100)
                .imageUrl("https://example.com/headphones.jpg")
                .categoryId(categoryElectronics.getId())
                .enabled(true)
                .build();

        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Wireless Noise Canceling Headphones"))
                .andExpect(jsonPath("$.price").value(199.99));
    }

    @Test
    void createProduct_InvalidPrice_Returns400BadRequest() throws Exception {
        ProductRequestDto request = ProductRequestDto.builder()
                .name("Invalid Price Product")
                .price(new BigDecimal("-10.00")) // Invalid negative price
                .stockQuantity(10)
                .categoryId(categoryElectronics.getId())
                .build();

        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.price").exists());
    }

    @Test
    void createProduct_InvalidStock_Returns400BadRequest() throws Exception {
        ProductRequestDto request = ProductRequestDto.builder()
                .name("Invalid Stock Product")
                .price(new BigDecimal("10.00"))
                .stockQuantity(-5) // Invalid negative stock
                .categoryId(categoryElectronics.getId())
                .build();

        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.stockQuantity").exists());
    }

    @Test
    void createProduct_MissingCategory_Returns404NotFound() throws Exception {
        ProductRequestDto request = ProductRequestDto.builder()
                .name("Product Nonexistent Cat")
                .price(new BigDecimal("50.00"))
                .stockQuantity(10)
                .categoryId(999999L) // Nonexistent category ID
                .build();

        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createProduct_Unauthenticated_Returns401Unauthorized() throws Exception {
        ProductRequestDto request = ProductRequestDto.builder()
                .name("Unauthenticated Product")
                .price(new BigDecimal("50.00"))
                .stockQuantity(10)
                .categoryId(categoryElectronics.getId())
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProduct_RoleUser_Returns403Forbidden() throws Exception {
        ProductRequestDto request = ProductRequestDto.builder()
                .name("Forbidden Product")
                .price(new BigDecimal("50.00"))
                .stockQuantity(10)
                .categoryId(categoryElectronics.getId())
                .build();

        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProduct_Admin_Returns200Ok() throws Exception {
        ProductRequestDto request = ProductRequestDto.builder()
                .name("Flagship Smartphone Pro Max")
                .description("Updated smartphone description")
                .price(new BigDecimal("1099.99"))
                .stockQuantity(40)
                .imageUrl("https://example.com/phone_updated.jpg")
                .categoryId(categoryElectronics.getId())
                .enabled(true)
                .build();

        mockMvc.perform(put("/api/products/" + productSmartphone.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Flagship Smartphone Pro Max"))
                .andExpect(jsonPath("$.price").value(1099.99));
    }

    @Test
    void deleteProduct_Admin_SoftDisablesProduct() throws Exception {
        mockMvc.perform(delete("/api/products/" + productSmartphone.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());

        // Verify product is now disabled and no longer visible in public lookup
        mockMvc.perform(get("/api/products/" + productSmartphone.getId()))
                .andExpect(status().isNotFound());
    }
}
