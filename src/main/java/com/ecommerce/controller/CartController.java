package com.ecommerce.controller;

import com.ecommerce.dto.AddCartItemRequestDto;
import com.ecommerce.dto.CartResponseDto;
import com.ecommerce.dto.UpdateCartItemRequestDto;
import com.ecommerce.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponseDto> getCart() {
        CartResponseDto cart = cartService.getCartForCurrentUser();
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponseDto> addCartItem(@Valid @RequestBody AddCartItemRequestDto dto) {
        CartResponseDto cart = cartService.addCartItem(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(cart);
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponseDto> updateCartItemQuantity(
            @PathVariable UUID cartItemId,
            @Valid @RequestBody UpdateCartItemRequestDto dto) {
        CartResponseDto cart = cartService.updateCartItemQuantity(cartItemId, dto);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponseDto> removeCartItem(@PathVariable UUID cartItemId) {
        CartResponseDto cart = cartService.removeCartItem(cartItemId);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping
    public ResponseEntity<CartResponseDto> clearCart() {
        CartResponseDto cart = cartService.clearCart();
        return ResponseEntity.ok(cart);
    }
}
