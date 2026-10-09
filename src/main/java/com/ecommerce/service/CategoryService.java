package com.ecommerce.service;

import com.ecommerce.dto.CategoryRequestDto;
import com.ecommerce.dto.CategoryResponseDto;
import com.ecommerce.entity.Category;
import com.ecommerce.exception.ResourceConflictException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service implementation for Product Category management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponseDto> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryResponseDto getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        return mapToResponseDto(category);
    }

    @Transactional
    public CategoryResponseDto createCategory(CategoryRequestDto dto) {
        if (categoryRepository.existsByNameIgnoreCase(dto.getName().trim())) {
            throw new ResourceConflictException(
                    String.format("Category with name '%s' already exists", dto.getName().trim()));
        }

        Category category = new Category();
        category.setName(dto.getName().trim());
        category.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);

        if (dto.getParentId() != null) {
            Category parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Category", "id", dto.getParentId()));
            category.setParent(parent);
        }

        Category savedCategory = categoryRepository.save(category);
        log.info("Created new category: ID={}, Name='{}'", savedCategory.getId(), savedCategory.getName());
        return mapToResponseDto(savedCategory);
    }

    @Transactional
    public CategoryResponseDto updateCategory(Long id, CategoryRequestDto dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        String newName = dto.getName().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(newName, id)) {
            throw new ResourceConflictException(
                    String.format("Category with name '%s' already exists", newName));
        }

        category.setName(newName);
        category.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);

        if (dto.getParentId() != null) {
            if (id.equals(dto.getParentId())) {
                throw new ResourceConflictException("Category cannot be its own parent");
            }
            Category parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Category", "id", dto.getParentId()));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        Category updatedCategory = categoryRepository.save(category);
        log.info("Updated category: ID={}, Name='{}'", updatedCategory.getId(), updatedCategory.getName());
        return mapToResponseDto(updatedCategory);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        long productCount = productRepository.countByCategoryId(id);
        if (productCount > 0) {
            throw new ResourceConflictException(
                    String.format("Cannot delete category '%s' because it is referenced by %d product(s)", category.getName(), productCount));
        }

        List<Category> subcategories = categoryRepository.findByParentId(id);
        if (!subcategories.isEmpty()) {
            throw new ResourceConflictException(
                    String.format("Cannot delete category '%s' because it contains %d subcategories", category.getName(), subcategories.size()));
        }

        categoryRepository.delete(category);
        log.info("Deleted category: ID={}, Name='{}'", id, category.getName());
    }

    private CategoryResponseDto mapToResponseDto(Category category) {
        Long parentId = category.getParent() != null ? category.getParent().getId() : null;
        String parentName = category.getParent() != null ? category.getParent().getName() : null;

        List<CategoryResponseDto> subcategories = categoryRepository.findByParentId(category.getId()).stream()
                .map(sub -> CategoryResponseDto.builder()
                        .id(sub.getId())
                        .name(sub.getName())
                        .description(sub.getDescription())
                        .parentId(category.getId())
                        .parentName(category.getName())
                        .build())
                .collect(Collectors.toList());

        return CategoryResponseDto.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .parentId(parentId)
                .parentName(parentName)
                .subcategories(subcategories)
                .build();
    }
}
