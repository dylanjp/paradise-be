package com.dylanjohnpratt.paradise.be.integration;

import com.dylanjohnpratt.paradise.be.dto.ChangePasswordRequest;
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
 * Integration tests for self-service password change.
 * 
 * Feature: jwt-authentication, Property 8: Self-Password Change Authorization
 * Validates: Requirements 7.1, 7.2, 7.3
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class SelfPasswordChangeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        // Clear existing users
        userRepository.deleteAll();

        // Create test user
        User user = new User("passworduser", passwordEncoder.encode("oldpassword"), 
                Set.of("ROLE_USER"));
        userRepository.save(user);

        // Get user token
        userToken = obtainToken("passworduser", "oldpassword");
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
    @DisplayName("Password change succeeds with correct current password")
    void passwordChangeSucceedsWithCorrectCurrentPassword() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("oldpassword", "newpassword123");
        
        mockMvc.perform(put("/users/me/password")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        // Verify new password works by logging in with it
        LoginRequest loginRequest = new LoginRequest("passworduser", "newpassword123");
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Password change fails with incorrect current password")
    void passwordChangeFailsWithIncorrectCurrentPassword() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("wrongpassword", "newpassword123");
        
        mockMvc.perform(put("/users/me/password")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Current password is incorrect"));
    }

    @Test
    @DisplayName("Password change requires authentication")
    void passwordChangeRequiresAuthentication() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("oldpassword", "newpassword123");
        
        // Spring Security returns 403 for unauthenticated requests by default
        mockMvc.perform(put("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Old password no longer works after password change")
    void oldPasswordNoLongerWorksAfterChange() throws Exception {
        // Change password
        ChangePasswordRequest request = new ChangePasswordRequest("oldpassword", "newpassword123");
        
        mockMvc.perform(put("/users/me/password")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Try to login with old password - should fail
        LoginRequest loginRequest = new LoginRequest("passworduser", "oldpassword");
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }
}
