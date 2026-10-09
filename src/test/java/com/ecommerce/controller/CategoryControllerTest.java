package com.ecommerce.controller;

import com.ecommerce.dto.CategoryRequestDto;
import com.ecommerce.entity.Category;
import com.ecommerce.repository.CategoryRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setName("Electronics Test Cat");
        category.setDescription("Electronic devices and gadgets");
        testCategory = categoryRepository.save(category);
    }

    @Test
    void getAllCategories_PublicAccess_Returns200Ok() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[?(@.name == 'Electronics Test Cat')]").exists());
    }

    @Test
    void getCategoryById_PublicAccess_Returns200Ok() throws Exception {
        mockMvc.perform(get("/api/categories/" + testCategory.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testCategory.getId()))
                .andExpect(jsonPath("$.name").value("Electronics Test Cat"));
    }

    @Test
    void getCategoryById_Missing_Returns404NotFound() throws Exception {
        mockMvc.perform(get("/api/categories/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createCategory_Admin_Returns201Created() throws Exception {
        CategoryRequestDto request = CategoryRequestDto.builder()
                .name("Books Test Cat")
                .description("Books and literature")
                .build();

        mockMvc.perform(post("/api/categories")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Books Test Cat"));
    }

    @Test
    void createCategory_DuplicateName_Returns409Conflict() throws Exception {
        CategoryRequestDto request = CategoryRequestDto.builder()
                .name("Electronics Test Cat")
                .description("Duplicate name check")
                .build();

        mockMvc.perform(post("/api/categories")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createCategory_Unauthenticated_Returns401Unauthorized() throws Exception {
        CategoryRequestDto request = CategoryRequestDto.builder()
                .name("Fashion Test Cat")
                .build();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCategory_RoleUser_Returns403Forbidden() throws Exception {
        CategoryRequestDto request = CategoryRequestDto.builder()
                .name("Fashion Test Cat")
                .build();

        mockMvc.perform(post("/api/categories")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCategory_Admin_Returns200Ok() throws Exception {
        CategoryRequestDto request = CategoryRequestDto.builder()
                .name("Updated Electronics Name")
                .description("Updated description")
                .build();

        mockMvc.perform(put("/api/categories/" + testCategory.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Electronics Name"));
    }

    @Test
    void deleteCategory_Admin_Returns204NoContent() throws Exception {
        mockMvc.perform(delete("/api/categories/" + testCategory.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
    }
}
