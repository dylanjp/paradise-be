package com.dylanjohnpratt.paradise.be.integration;

import com.dylanjohnpratt.paradise.be.dto.LoginRequest;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authorization enforcement.
 * 
 * Feature: jwt-authentication, Property 6: Authorization Enforcement
 * Validates: Requirements 5.1, 5.2, 5.4
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class AuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        // Clear existing users
        userRepository.deleteAll();

        // Create admin user
        User admin = new User("testadmin", passwordEncoder.encode("adminpass"), 
                Set.of("ROLE_ADMIN", "ROLE_USER"));
        userRepository.save(admin);

        // Create regular user
        User user = new User("testuser", passwordEncoder.encode("userpass"), 
                Set.of("ROLE_USER"));
        userRepository.save(user);

        // Get admin token
        adminToken = obtainToken("testadmin", "adminpass");
        
        // Get user token
        userToken = obtainToken("testuser", "userpass");
    }

    private String obtainToken(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, password);
        
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    @DisplayName("Admin endpoints require ROLE_ADMIN - admin user can access")
    void adminEndpointsAccessibleByAdmin() throws Exception {
        // Admin should be able to access /admin/** endpoints - POST /admin/users creates a user
        mockMvc.perform(post("/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"password\":\"pass\",\"roles\":[\"ROLE_USER\"]}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Admin endpoints require ROLE_ADMIN - regular user gets 403")
    void adminEndpointsDeniedForRegularUser() throws Exception {
        // Regular user should get 403 Forbidden for /admin/** endpoints
        mockMvc.perform(post("/admin/users")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"password\":\"pass\",\"roles\":[\"ROLE_USER\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Non-auth endpoints require authentication - returns 403 without token")
    void nonAuthEndpointsRequireAuthentication() throws Exception {
        // Accessing protected endpoint without token - Spring Security returns 403 by default
        mockMvc.perform(put("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"test\",\"newPassword\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Auth endpoints are accessible without authentication")
    void authEndpointsAccessibleWithoutToken() throws Exception {
        // /auth/** endpoints should be accessible without authentication
        LoginRequest loginRequest = new LoginRequest("testuser", "userpass");
        
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Authenticated user without required role gets 403")
    void insufficientRolesReturns403() throws Exception {
        // User with ROLE_USER trying to access admin endpoint should get 403
        mockMvc.perform(put("/admin/users/1/roles")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ROLE_ADMIN\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Invalid JWT token returns 403")
    void invalidTokenReturns403() throws Exception {
        // Spring Security returns 403 for invalid tokens by default
        mockMvc.perform(put("/users/me/password")
                .header("Authorization", "Bearer invalid.token.here")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"test\",\"newPassword\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Missing Bearer prefix returns 403")
    void missingBearerPrefixReturns403() throws Exception {
        // Spring Security returns 403 when Bearer prefix is missing
        mockMvc.perform(put("/users/me/password")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"test\",\"newPassword\":\"test\"}"))
                .andExpect(status().isForbidden());
    }
}
