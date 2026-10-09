package com.ecommerce.controller;

import com.ecommerce.dto.AddressRequestDto;
import com.ecommerce.entity.*;
import com.ecommerce.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Customer Address module (Stage 10).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private OrderRepository orderRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setId(UUID.randomUUID());
        userA.setEmail("usera-addr-" + UUID.randomUUID() + "@example.com");
        userA.setFullName("User Alpha Address");
        userA.setRole(UserRole.ROLE_USER);
        userA.setEnabled(true);
        userA = userRepository.save(userA);

        userB = new User();
        userB.setId(UUID.randomUUID());
        userB.setEmail("userb-addr-" + UUID.randomUUID() + "@example.com");
        userB.setFullName("User Beta Address");
        userB.setRole(UserRole.ROLE_USER);
        userB.setEnabled(true);
        userB = userRepository.save(userB);
    }

    private JwtRequestPostProcessor jwtUserA() {
        return jwt()
                .jwt(builder -> builder.subject(userA.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private JwtRequestPostProcessor jwtUserB() {
        return jwt()
                .jwt(builder -> builder.subject(userB.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // ------------------------------------------------------------------ //
    // 1. Unauthenticated & Authorization Tests                           //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("Authentication & Authorization Rules")
    class AuthorizationTests {

        @Test
        @DisplayName("Unauthenticated request to list addresses returns 401 Unauthorized")
        void getAddresses_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/addresses"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Unauthenticated request to create address returns 401 Unauthorized")
        void createAddress_Unauthenticated_Returns401() throws Exception {
            AddressRequestDto dto = AddressRequestDto.builder()
                    .street("123 Main St")
                    .city("Metropolis")
                    .country("USA")
                    .build();

            mockMvc.perform(post("/api/addresses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ //
    // 2. Address CRUD Operations                                         //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("Address Lifecycle Operations")
    class AddressCrudTests {

        @Test
        @DisplayName("Create first address automatically marks it default")
        void createAddress_FirstAddress_AutomaticallyDefault() throws Exception {
            AddressRequestDto dto = AddressRequestDto.builder()
                    .street("742 Evergreen Terrace")
                    .city("Springfield")
                    .state("IL")
                    .zip("62701")
                    .country("USA")
                    .isDefault(false) // Requesting false, but first address must default to true
                    .build();

            mockMvc.perform(post("/api/addresses")
                            .with(jwtUserA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.street").value("742 Evergreen Terrace"))
                    .andExpect(jsonPath("$.city").value("Springfield"))
                    .andExpect(jsonPath("$.default").value(true));
        }

        @Test
        @DisplayName("List addresses returns only authenticated user's addresses")
        void getMyAddresses_ReturnsUserAddressesOnly() throws Exception {
            Address addrA = new Address();
            addrA.setUser(userA);
            addrA.setStreet("User A Street");
            addrA.setCity("City A");
            addrA.setCountry("USA");
            addrA.setDefault(true);
            addressRepository.save(addrA);

            Address addrB = new Address();
            addrB.setUser(userB);
            addrB.setStreet("User B Street");
            addrB.setCity("City B");
            addrB.setCountry("USA");
            addrB.setDefault(true);
            addressRepository.save(addrB);

            mockMvc.perform(get("/api/addresses").with(jwtUserA()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].street").value("User A Street"));
        }

        @Test
        @DisplayName("Owner can update address fields")
        void updateAddress_Owner_Success() throws Exception {
            Address address = new Address();
            address.setUser(userA);
            address.setStreet("Old Street");
            address.setCity("Old City");
            address.setCountry("USA");
            address.setDefault(true);
            address = addressRepository.save(address);

            AddressRequestDto updateDto = AddressRequestDto.builder()
                    .street("New Street 456")
                    .city("New City")
                    .state("NY")
                    .zip("10001")
                    .country("USA")
                    .isDefault(true)
                    .build();

            mockMvc.perform(put("/api/addresses/" + address.getId())
                            .with(jwtUserA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.street").value("New Street 456"))
                    .andExpect(jsonPath("$.city").value("New City"));

            Address updated = addressRepository.findById(address.getId()).orElseThrow();
            assertEquals("New Street 456", updated.getStreet());
        }

        @Test
        @DisplayName("User B updating User A's address returns 404 Not Found")
        void updateAddress_CrossUser_Returns404() throws Exception {
            Address address = new Address();
            address.setUser(userA);
            address.setStreet("User A Private Street");
            address.setCity("City A");
            address.setCountry("USA");
            address = addressRepository.save(address);

            AddressRequestDto updateDto = AddressRequestDto.builder()
                    .street("Tampered Street")
                    .city("Tampered City")
                    .country("USA")
                    .build();

            mockMvc.perform(put("/api/addresses/" + address.getId())
                            .with(jwtUserB())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Owner can delete address when not referenced by orders")
        void deleteAddress_Owner_Success() throws Exception {
            Address address = new Address();
            address.setUser(userA);
            address.setStreet("Unused Street");
            address.setCity("City");
            address.setCountry("USA");
            address = addressRepository.save(address);

            mockMvc.perform(delete("/api/addresses/" + address.getId()).with(jwtUserA()))
                    .andExpect(status().isNoContent());

            assertFalse(addressRepository.existsById(address.getId()));
        }

        @Test
        @DisplayName("User B deleting User A's address returns 404 Not Found")
        void deleteAddress_CrossUser_Returns404() throws Exception {
            Address address = new Address();
            address.setUser(userA);
            address.setStreet("User A Address");
            address.setCity("City");
            address.setCountry("USA");
            address = addressRepository.save(address);

            mockMvc.perform(delete("/api/addresses/" + address.getId()).with(jwtUserB()))
                    .andExpect(status().isNotFound());

            assertTrue(addressRepository.existsById(address.getId()));
        }

        @Test
        @DisplayName("Deleting address referenced by existing order returns 409 Conflict")
        void deleteAddress_ReferencedByOrder_Returns409Conflict() throws Exception {
            Address address = new Address();
            address.setUser(userA);
            address.setStreet("Order Snapshot Street");
            address.setCity("Order City");
            address.setCountry("USA");
            address = addressRepository.save(address);

            Order order = new Order();
            order.setUser(userA);
            order.setAddress(address);
            order.setStatus(OrderStatus.PENDING);
            order.setTotalAmount(new BigDecimal("99.99"));
            orderRepository.save(order);

            mockMvc.perform(delete("/api/addresses/" + address.getId()).with(jwtUserA()))
                    .andExpect(status().isConflict());

            assertTrue(addressRepository.existsById(address.getId()));
        }

        @Test
        @DisplayName("Address creation with missing street or city returns 400 Bad Request")
        void createAddress_InvalidValidation_Returns400() throws Exception {
            AddressRequestDto invalidDto = AddressRequestDto.builder()
                    .street("") // blank
                    .city("Some City")
                    .country("USA")
                    .build();

            mockMvc.perform(post("/api/addresses")
                            .with(jwtUserA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidDto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Owner can mark non-default address as default via PUT /api/addresses/{addressId}/default")
        void setDefaultAddress_Owner_Success() throws Exception {
            Address address1 = new Address();
            address1.setUser(userA);
            address1.setStreet("First Street");
            address1.setCity("City");
            address1.setCountry("USA");
            address1.setDefault(true);
            addressRepository.save(address1);

            Address address2 = new Address();
            address2.setUser(userA);
            address2.setStreet("Second Street");
            address2.setCity("City");
            address2.setCountry("USA");
            address2.setDefault(false);
            addressRepository.save(address2);

            mockMvc.perform(put("/api/addresses/" + address2.getId() + "/default")
                            .with(jwtUserA()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(address2.getId().toString()))
                    .andExpect(jsonPath("$.default").value(true));

            Address updated1 = addressRepository.findById(address1.getId()).orElseThrow();
            Address updated2 = addressRepository.findById(address2.getId()).orElseThrow();
            assertFalse(updated1.isDefault());
            assertTrue(updated2.isDefault());
        }
    }
}
