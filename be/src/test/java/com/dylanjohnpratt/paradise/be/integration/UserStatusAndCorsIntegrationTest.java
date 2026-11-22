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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the contract the frontend relies on:
 * <ul>
 *   <li>{@code PUT /admin/users/{id}/status {enabled}} — called by the admin page's enable/disable toggle</li>
 *   <li>CORS preflight allows {@code PATCH} — used by the health appointment and reminder update endpoints</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@SuppressWarnings("null")
class UserStatusAndCorsIntegrationTest {

    private static final String DEV_ORIGIN = "http://localhost:3000";

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
    private Long userId;
    private String userName;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminName = "statusadmin_" + suffix;
        userName = "statususer_" + suffix;

        userRepository.save(new User(adminName, passwordEncoder.encode("adminpass"),
                Set.of("ROLE_ADMIN", "ROLE_USER")));
        userId = userRepository.save(new User(userName, passwordEncoder.encode("userpass"),
                Set.of("ROLE_USER"))).getId();

        adminToken = obtainToken(adminName, "adminpass");
        userToken = obtainToken(userName, "userpass");
    }

    private String obtainToken(String username, String password) throws Exception {
        MvcResult result = login(username, password)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))));
    }

    private ResultActions putStatus(String token, Long id, String body) throws Exception {
        return mockMvc.perform(put("/admin/users/{id}/status", id)
                .header("Authorization", "Bearer " + token)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(body));
    }

    @Test
    @DisplayName("Admin can disable and re-enable a user through /status")
    void adminCanDisableAndReEnableUser() throws Exception {
        String username = userRepository.findById(userId).orElseThrow().getUsername();

        putStatus(adminToken, userId, "{\"enabled\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.enabled").value(false));

        login(username, "userpass")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Account is disabled"));

        putStatus(adminToken, userId, "{\"enabled\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        login(username, "userpass").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Disabling a user revokes their existing token; re-enabling restores it")
    void disablingRevokesExistingToken() throws Exception {
        mockMvc.perform(get("/users/{userId}/tasks", userName)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        putStatus(adminToken, userId, "{\"enabled\":false}").andExpect(status().isOk());

        // Same unexpired token, now rejected (403: there is no 401 entry point)
        mockMvc.perform(get("/users/{userId}/tasks", userName)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        putStatus(adminToken, userId, "{\"enabled\":true}").andExpect(status().isOk());

        mockMvc.perform(get("/users/{userId}/tasks", userName)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/status requires ROLE_ADMIN")
    void statusRequiresAdmin() throws Exception {
        putStatus(userToken, userId, "{\"enabled\":false}")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/status rejects a body without enabled")
    void statusRequiresEnabledField() throws Exception {
        putStatus(adminToken, userId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("enabled is required"));
    }

    @Test
    @DisplayName("/status returns 404 for an unknown user")
    void statusUnknownUserReturns404() throws Exception {
        putStatus(adminToken, Long.MAX_VALUE, "{\"enabled\":true}")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Legacy /disable endpoint still works")
    void legacyDisableStillWorks() throws Exception {
        mockMvc.perform(put("/admin/users/{id}/disable", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(
                userRepository.findById(userId).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("CORS preflight allows PATCH from an allowed origin")
    void corsPreflightAllowsPatch() throws Exception {
        mockMvc.perform(options("/users/someone/health/reminders/r1")
                        .header("Origin", DEV_ORIGIN)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", DEV_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")));
    }

    @Test
    @DisplayName("CORS preflight rejects an origin that is not allowed")
    void corsPreflightRejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/users/someone/health/reminders/r1")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isForbidden());
    }
}
