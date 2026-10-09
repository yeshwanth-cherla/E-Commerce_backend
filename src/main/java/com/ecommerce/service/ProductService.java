package com.ecommerce.service;

import com.ecommerce.dto.CategoryResponseDto;
import com.ecommerce.dto.PaginatedResponseDto;
import com.ecommerce.dto.ProductRequestDto;
import com.ecommerce.dto.ProductResponseDto;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Service implementation for Product catalogue management, search, filtering, and pagination.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "price", "createdAt", "averageRating", "stockQuantity");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public PaginatedResponseDto<ProductResponseDto> getProducts(
            String search,
            Long categoryId,
            Boolean includeDisabled,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        // Sanitize page number and page size limits
        int sanitizedPage = Math.max(page, 0);
        int sanitizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        // Validate and sanitize sort field to prevent SQL injection / invalid property queries
        String validSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        Sort.Direction validSortDir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by(validSortDir, validSortBy));

        // Trim search term if provided
        String sanitizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        // Default to enabled products only unless explicitly requested
        boolean enabledOnly = (includeDisabled == null || !includeDisabled);

        Page<Product> productPage = productRepository.findProductsWithFilters(
                sanitizedSearch,
                categoryId,
                enabledOnly,
                pageable);

        Page<ProductResponseDto> dtoPage = productPage.map(this::mapToResponseDto);
        return PaginatedResponseDto.fromPage(dtoPage);
    }

    @Transactional(readOnly = true)
    public ProductResponseDto getProductById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (!product.isEnabled()) {
            throw new ResourceNotFoundException("Product", "id", id);
        }

        return mapToResponseDto(product);
    }

    @Transactional
    public ProductResponseDto createProduct(ProductRequestDto dto) {
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", dto.getCategoryId()));

        Product product = new Product();
        product.setName(dto.getName().trim());
        product.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);
        product.setPrice(dto.getPrice());
        product.setStockQuantity(dto.getStockQuantity());
        product.setImageUrl(dto.getImageUrl() != null ? dto.getImageUrl().trim() : null);
        product.setCategory(category);
        product.setEnabled(dto.isEnabled());

        Product savedProduct = productRepository.save(product);
        log.info("Created new product: ID={}, Name='{}'", savedProduct.getId(), savedProduct.getName());
        return mapToResponseDto(savedProduct);
    }

    @Transactional
    public ProductResponseDto updateProduct(UUID id, ProductRequestDto dto) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", dto.getCategoryId()));

        product.setName(dto.getName().trim());
        product.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);
        product.setPrice(dto.getPrice());
        product.setStockQuantity(dto.getStockQuantity());
        product.setImageUrl(dto.getImageUrl() != null ? dto.getImageUrl().trim() : null);
        product.setCategory(category);
        product.setEnabled(dto.isEnabled());

        Product updatedProduct = productRepository.save(product);
        log.info("Updated product: ID={}, Name='{}'", updatedProduct.getId(), updatedProduct.getName());
        return mapToResponseDto(updatedProduct);
    }

    @Transactional
    public void disableProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        // Soft deletion — set enabled=false to preserve historical OrderItem references
        product.setEnabled(false);
        productRepository.save(product);
        log.info("Soft-disabled product: ID={}, Name='{}'", id, product.getName());
    }

    private ProductResponseDto mapToResponseDto(Product product) {
        CategoryResponseDto categoryDto = CategoryResponseDto.builder()
                .id(product.getCategory().getId())
                .name(product.getCategory().getName())
                .description(product.getCategory().getDescription())
                .build();

        return ProductResponseDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .imageUrl(product.getImageUrl())
                .category(categoryDto)
                .averageRating(product.getAverageRating())
                .enabled(product.isEnabled())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
