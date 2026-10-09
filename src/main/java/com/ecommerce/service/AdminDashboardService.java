package com.ecommerce.service;

import com.ecommerce.dto.AdminDashboardResponseDto;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.PaymentStatus;
import com.ecommerce.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

/**
 * Computes high-level aggregate metrics for the administrator dashboard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponseDto getDashboardMetrics() {
        long totalUsers = userRepository.count();
        long totalEnabledProducts = productRepository.countByEnabledTrue();
        long totalCategories = categoryRepository.count();
        long totalOrders = orderRepository.count();

        Map<OrderStatus, Long> ordersByStatus = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            ordersByStatus.put(status, orderRepository.countByStatus(status));
        }

        long pendingPayments = paymentRepository.countByStatus(PaymentStatus.PENDING);
        long successfulPayments = paymentRepository.countByStatus(PaymentStatus.SUCCESS);
        long totalReviews = reviewRepository.count();
        long lowStockProducts = productRepository.countByStockQuantityLessThanEqual(10);

        log.info("Admin dashboard metrics computed: users={}, enabledProducts={}, orders={}, lowStock={}",
                totalUsers, totalEnabledProducts, totalOrders, lowStockProducts);

        return AdminDashboardResponseDto.builder()
                .totalUsers(totalUsers)
                .totalEnabledProducts(totalEnabledProducts)
                .totalCategories(totalCategories)
                .totalOrders(totalOrders)
                .ordersByStatus(ordersByStatus)
                .pendingPayments(pendingPayments)
                .successfulPayments(successfulPayments)
                .totalReviews(totalReviews)
                .lowStockProducts(lowStockProducts)
                .build();
    }
}
