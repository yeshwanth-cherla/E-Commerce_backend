package com.ecommerce.service;

import com.ecommerce.dto.AddCartItemRequestDto;
import com.ecommerce.dto.CartItemResponseDto;
import com.ecommerce.dto.CartResponseDto;
import com.ecommerce.dto.UpdateCartItemRequestDto;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceConflictException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CartResponseDto getCartForCurrentUser() {
        UUID currentUserId = getCurrentAuthenticatedUserId();
        Cart cart = getOrCreateCart(currentUserId);
        return mapToCartResponseDto(cart);
    }

    @Transactional
    public CartResponseDto addCartItem(AddCartItemRequestDto dto) {
        UUID currentUserId = getCurrentAuthenticatedUserId();
        Cart cart = getOrCreateCart(currentUserId);

        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id : '" + dto.getProductId() + "'"));

        if (!product.isEnabled()) {
            throw new ResourceConflictException("Cannot add disabled product to cart");
        }

        // Check if item already exists in user's cart
        Optional<CartItem> existingItemOpt = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId());

        int currentQtyInCart = existingItemOpt.map(CartItem::getQuantity).orElse(0);
        int targetQuantity = currentQtyInCart + dto.getQuantity();

        if (targetQuantity > product.getStockQuantity()) {
            throw new ResourceConflictException("Requested quantity (" + targetQuantity + 
                    ") exceeds available stock (" + product.getStockQuantity() + ")");
        }

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            existingItem.setQuantity(targetQuantity);
            cartItemRepository.save(existingItem);
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(targetQuantity);
            cart.getCartItems().add(newItem);
            cartRepository.save(cart);
        }

        log.info("Added product ID={} to cart for user ID={}, targetQuantity={}", product.getId(), currentUserId, targetQuantity);
        return mapToCartResponseDto(cart);
    }

    @Transactional
    public CartResponseDto updateCartItemQuantity(UUID cartItemId, UpdateCartItemRequestDto dto) {
        UUID currentUserId = getCurrentAuthenticatedUserId();

        CartItem cartItem = cartItemRepository.findByIdAndCartUserId(cartItemId, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id : '" + cartItemId + "'"));

        Product product = cartItem.getProduct();
        if (!product.isEnabled()) {
            throw new ResourceConflictException("Cannot update item for disabled product");
        }

        if (dto.getQuantity() > product.getStockQuantity()) {
            throw new ResourceConflictException("Requested quantity (" + dto.getQuantity() + 
                    ") exceeds available stock (" + product.getStockQuantity() + ")");
        }

        cartItem.setQuantity(dto.getQuantity());
        cartItemRepository.save(cartItem);

        Cart cart = cartItem.getCart();
        log.info("Updated cart item ID={} quantity to {} for user ID={}", cartItemId, dto.getQuantity(), currentUserId);
        return mapToCartResponseDto(cart);
    }

    @Transactional
    public CartResponseDto removeCartItem(UUID cartItemId) {
        UUID currentUserId = getCurrentAuthenticatedUserId();

        CartItem cartItem = cartItemRepository.findByIdAndCartUserId(cartItemId, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id : '" + cartItemId + "'"));

        Cart cart = cartItem.getCart();
        cart.getCartItems().remove(cartItem);
        cartItemRepository.delete(cartItem);

        log.info("Removed cart item ID={} for user ID={}", cartItemId, currentUserId);
        return mapToCartResponseDto(cart);
    }

    @Transactional
    public CartResponseDto clearCart() {
        UUID currentUserId = getCurrentAuthenticatedUserId();
        Cart cart = getOrCreateCart(currentUserId);

        cart.getCartItems().clear();
        cartRepository.save(cart);

        log.info("Cleared cart for user ID={}", currentUserId);
        return mapToCartResponseDto(cart);
    }

    private UUID getCurrentAuthenticatedUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
    }

    private Cart getOrCreateCart(UUID userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id : '" + userId + "'"));
            Cart newCart = new Cart();
            newCart.setUser(user);
            return cartRepository.save(newCart);
        });
    }

    private CartResponseDto mapToCartResponseDto(Cart cart) {
        List<CartItemResponseDto> itemDtos = new ArrayList<>();
        BigDecimal cartTotal = BigDecimal.ZERO;
        int itemCount = 0;

        if (cart.getCartItems() != null) {
            for (CartItem item : cart.getCartItems()) {
                Product p = item.getProduct();
                BigDecimal price = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
                BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(item.getQuantity()));

                cartTotal = cartTotal.add(lineTotal);
                itemCount += item.getQuantity();

                CartItemResponseDto itemDto = CartItemResponseDto.builder()
                        .id(item.getId())
                        .productId(p.getId())
                        .productName(p.getName())
                        .productImageUrl(p.getImageUrl())
                        .productPrice(price)
                        .quantity(item.getQuantity())
                        .lineTotal(lineTotal)
                        .productEnabled(p.isEnabled())
                        .productStock(p.getStockQuantity())
                        .build();

                itemDtos.add(itemDto);
            }
        }

        return CartResponseDto.builder()
                .id(cart.getId())
                .items(itemDtos)
                .itemCount(itemCount)
                .cartTotal(cartTotal)
                .build();
    }
}
