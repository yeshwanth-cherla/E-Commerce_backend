package com.ecommerce.repository;

import com.ecommerce.entity.InventoryAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    @EntityGraph(attributePaths = {"product"})
    Page<InventoryAdjustment> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    @EntityGraph(attributePaths = {"product"})
    Page<InventoryAdjustment> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
