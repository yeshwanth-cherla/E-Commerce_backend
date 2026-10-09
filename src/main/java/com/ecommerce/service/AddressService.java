package com.ecommerce.service;

import com.ecommerce.dto.AddressRequestDto;
import com.ecommerce.dto.AddressResponseDto;
import com.ecommerce.entity.Address;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceConflictException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.AddressRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles delivery address management for customers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    /**
     * Returns all delivery addresses belonging to the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<AddressResponseDto> getAddressesForCurrentUser() {
        UUID currentUserId = requireCurrentUserId();

        return addressRepository.findByUserId(currentUserId).stream()
                .map(this::mapToAddressResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Creates a new delivery address for the authenticated user.
     */
    @Transactional
    public AddressResponseDto createAddress(AddressRequestDto dto) {
        UUID currentUserId = requireCurrentUserId();

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id : '" + currentUserId + "'"));

        List<Address> existingAddresses = addressRepository.findByUserId(currentUserId);
        boolean makeDefault = existingAddresses.isEmpty() || Boolean.TRUE.equals(dto.getIsDefault());

        if (makeDefault) {
            unsetExistingDefaultAddress(currentUserId);
        }

        Address address = new Address();
        address.setUser(user);
        address.setStreet(dto.getStreet());
        address.setCity(dto.getCity());
        address.setState(dto.getState());
        address.setZip(dto.getZip());
        address.setCountry(dto.getCountry());
        address.setDefault(makeDefault);

        Address savedAddress = addressRepository.save(address);

        log.info("Address created: addressId={}, userId={}, isDefault={}",
                savedAddress.getId(), currentUserId, savedAddress.isDefault());

        return mapToAddressResponseDto(savedAddress);
    }

    /**
     * Updates an existing delivery address owned by the authenticated user.
     */
    @Transactional
    public AddressResponseDto updateAddress(UUID addressId, AddressRequestDto dto) {
        UUID currentUserId = requireCurrentUserId();

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id : '" + addressId + "'"));

        if (!address.getUser().getId().equals(currentUserId)) {
            throw new ResourceNotFoundException("Address not found with id : '" + addressId + "'");
        }

        if (Boolean.TRUE.equals(dto.getIsDefault()) && !address.isDefault()) {
            unsetExistingDefaultAddress(currentUserId);
            address.setDefault(true);
        } else if (Boolean.FALSE.equals(dto.getIsDefault())) {
            address.setDefault(false);
        }

        address.setStreet(dto.getStreet());
        address.setCity(dto.getCity());
        address.setState(dto.getState());
        address.setZip(dto.getZip());
        address.setCountry(dto.getCountry());

        Address updatedAddress = addressRepository.save(address);

        log.info("Address updated: addressId={}, userId={}", addressId, currentUserId);

        return mapToAddressResponseDto(updatedAddress);
    }

    /**
     * Deletes a delivery address owned by the authenticated user if not tied to existing orders.
     */
    @Transactional
    public void deleteAddress(UUID addressId) {
        UUID currentUserId = requireCurrentUserId();

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id : '" + addressId + "'"));

        if (!address.getUser().getId().equals(currentUserId)) {
            throw new ResourceNotFoundException("Address not found with id : '" + addressId + "'");
        }

        if (orderRepository.existsByAddressId(addressId)) {
            throw new ResourceConflictException("Cannot delete address associated with existing orders");
        }

        boolean WASDefault = address.isDefault();
        addressRepository.delete(address);

        // If the default address was deleted, promote another existing address to default if available
        if (WASDefault) {
            List<Address> remaining = addressRepository.findByUserId(currentUserId);
            if (!remaining.isEmpty()) {
                Address nextDefault = remaining.get(0);
                nextDefault.setDefault(true);
                addressRepository.save(nextDefault);
            }
        }

        log.info("Address deleted: addressId={}, userId={}", addressId, currentUserId);
    }

    /**
     * Sets a delivery address owned by the authenticated user as default.
     */
    @Transactional
    public AddressResponseDto setDefaultAddress(UUID addressId) {
        UUID currentUserId = requireCurrentUserId();

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id : '" + addressId + "'"));

        if (!address.getUser().getId().equals(currentUserId)) {
            throw new ResourceNotFoundException("Address not found with id : '" + addressId + "'");
        }

        if (!address.isDefault()) {
            unsetExistingDefaultAddress(currentUserId);
            address.setDefault(true);
            address = addressRepository.save(address);
        }

        log.info("Address set as default: addressId={}, userId={}", addressId, currentUserId);

        return mapToAddressResponseDto(address);
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                            //
    // ------------------------------------------------------------------ //

    private void unsetExistingDefaultAddress(UUID userId) {
        Optional<Address> currentDefault = addressRepository.findByUserIdAndIsDefaultTrue(userId);
        if (currentDefault.isPresent()) {
            Address def = currentDefault.get();
            def.setDefault(false);
            addressRepository.save(def);
        }
    }

    private UUID requireCurrentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
    }

    private AddressResponseDto mapToAddressResponseDto(Address address) {
        return AddressResponseDto.builder()
                .id(address.getId())
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .zip(address.getZip())
                .country(address.getCountry())
                .isDefault(address.isDefault())
                .build();
    }
}
