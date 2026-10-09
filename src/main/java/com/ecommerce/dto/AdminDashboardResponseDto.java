package com.ecommerce.dto;

import com.ecommerce.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Aggregate system metrics for the administrator dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardResponseDto {

    private long totalUsers;
    private long totalEnabledProducts;
    private long totalCategories;
    private long totalOrders;
    private Map<OrderStatus, Long> ordersByStatus;
    private long pendingPayments;
    private long successfulPayments;
    private long totalReviews;
    private long lowStockProducts;
}
