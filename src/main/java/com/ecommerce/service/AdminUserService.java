package com.ecommerce.service;

import com.ecommerce.dto.OrderResponseDto;
import com.ecommerce.dto.OrderResponseDto.AddressSnapshotDto;
import com.ecommerce.dto.OrderItemResponseDto;
import com.ecommerce.dto.UpdateUserStatusRequestDto;
import com.ecommerce.dto.UserResponseDto;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceConflictException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles user management operations for administrators.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    /**
     * Lists users with search and pagination.
     */
    @Transactional(readOnly = true)
    public Page<UserResponseDto> getUsers(String search, Pageable pageable) {
        return userRepository.findBySearchFilters(search, pageable)
                .map(this::mapToUserResponseDto);
    }

    /**
     * Retrieves a single user's safe profile by ID.
     */
    @Transactional(readOnly = true)
    public UserResponseDto getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id : '" + userId + "'"));
        return mapToUserResponseDto(user);
    }

    /**
     * Enables or disables a user account.
     * Prevents an administrator from disabling their own account.
     */
    @Transactional
    public UserResponseDto updateUserStatus(UUID userId, UpdateUserStatusRequestDto dto) {
        UUID currentAdminId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));

        if (userId.equals(currentAdminId) && Boolean.FALSE.equals(dto.getEnabled())) {
            throw new ResourceConflictException("Administrators cannot disable their own account");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id : '" + userId + "'"));

        user.setEnabled(dto.getEnabled());
        User savedUser = userRepository.save(user);

        log.info("User status updated by admin: userId={}, enabled={}, adminId={}",
                userId, dto.getEnabled(), currentAdminId);

        return mapToUserResponseDto(savedUser);
    }

    /**
     * Lists order history for a specific user.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getUserOrders(UUID userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id : '" + userId + "'"));

        return orderRepository.findByUserOrderByCreatedAtDesc(user, pageable)
                .map(this::mapToOrderResponseDto);
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                            //
    // ------------------------------------------------------------------ //

    private UserResponseDto mapToUserResponseDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private OrderResponseDto mapToOrderResponseDto(Order order) {
        List<OrderItemResponseDto> itemDtos = order.getOrderItems().stream()
                .map(this::mapToOrderItemResponseDto)
                .collect(Collectors.toList());

        AddressSnapshotDto addressDto = AddressSnapshotDto.builder()
                .id(order.getAddress().getId())
                .street(order.getAddress().getStreet())
                .city(order.getAddress().getCity())
                .state(order.getAddress().getState())
                .zip(order.getAddress().getZip())
                .country(order.getAddress().getCountry())
                .build();

        return OrderResponseDto.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .address(addressDto)
                .items(itemDtos)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderItemResponseDto mapToOrderItemResponseDto(OrderItem item) {
        Product product = item.getProduct();
        BigDecimal lineTotal = item.getPriceAtPurchase()
                .multiply(BigDecimal.valueOf(item.getQuantity()));

        return OrderItemResponseDto.builder()
                .id(item.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productImageUrl(product.getImageUrl())
                .priceAtPurchase(item.getPriceAtPurchase())
                .quantity(item.getQuantity())
                .lineTotal(lineTotal)
                .build();
    }
}
