package com.ecommerce.repository;

import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByCart(Cart cart);

    Optional<CartItem> findByCartAndProduct(Cart cart, Product product);

    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

    @Query("SELECT ci FROM CartItem ci JOIN ci.cart c WHERE ci.id = :cartItemId AND c.user.id = :userId")
    Optional<CartItem> findByIdAndCartUserId(@Param("cartItemId") UUID cartItemId, @Param("userId") UUID userId);

    void deleteByCart(Cart cart);
}
