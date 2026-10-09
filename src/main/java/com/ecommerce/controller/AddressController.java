package com.ecommerce.controller;

import com.ecommerce.dto.AddressRequestDto;
import com.ecommerce.dto.AddressResponseDto;
import com.ecommerce.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Customer Delivery Address management.
 *
 * All endpoints require authentication.
 */
@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /**
     * Lists all delivery addresses for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<List<AddressResponseDto>> getMyAddresses() {
        List<AddressResponseDto> addresses = addressService.getAddressesForCurrentUser();
        return ResponseEntity.ok(addresses);
    }

    /**
     * Creates a new delivery address for the authenticated user.
     */
    @PostMapping
    public ResponseEntity<AddressResponseDto> createAddress(
            @Valid @RequestBody AddressRequestDto dto) {

        AddressResponseDto created = addressService.createAddress(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates an existing delivery address owned by the authenticated user.
     */
    @PutMapping("/{addressId}")
    public ResponseEntity<AddressResponseDto> updateAddress(
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequestDto dto) {

        AddressResponseDto updated = addressService.updateAddress(addressId, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * Deletes a delivery address owned by the authenticated user.
     */
    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable UUID addressId) {
        addressService.deleteAddress(addressId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sets a delivery address as default for the authenticated user.
     */
    @PutMapping("/{addressId}/default")
    public ResponseEntity<AddressResponseDto> setDefaultAddress(@PathVariable UUID addressId) {
        AddressResponseDto updated = addressService.setDefaultAddress(addressId);
        return ResponseEntity.ok(updated);
    }
}
