package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for returning customer address responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponseDto {

    private UUID id;
    private String street;
    private String city;
    private String state;
    private String zip;
    private String country;
    private boolean isDefault;
}
