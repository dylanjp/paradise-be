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

import java.util.Objects;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests pinning the strict-isolation invariant for the health module:
 * <ul>
 *   <li>Owners can read their own health endpoints (200).</li>
 *   <li>A different authenticated user requesting an owner's path gets 403 with
 *       {@code HEALTH_ACCESS_DENIED}.</li>
 *   <li>{@code ROLE_ADMIN} does <b>NOT</b> bypass — admins hitting another user's
 *       health path also get 403 {@code HEALTH_ACCESS_DENIED}.</li>
 *   <li>No bearer token → 403 (Spring Security gate on {@code /users/*\/health/**}).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class HealthAuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String ownerToken;
    private String impostorToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();

        userRepository.save(new User("owner",    passwordEncoder.encode("ownerpass"),    Set.of("ROLE_USER")));
        userRepository.save(new User("impostor", passwordEncoder.encode("impostorpass"), Set.of("ROLE_USER")));
        userRepository.save(new User("admin2",   passwordEncoder.encode("adminpass"),    Set.of("ROLE_ADMIN", "ROLE_USER")));

        ownerToken    = obtainToken("owner",    "ownerpass");
        impostorToken = obtainToken("impostor", "impostorpass");
        adminToken    = obtainToken("admin2",   "adminpass");
    }

    private String obtainToken(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(loginRequest))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("Owner can read own journal (200)")
    void ownerCanReadOwnJournal() throws Exception {
        mockMvc.perform(get("/users/owner/health/journal")
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Impostor reading owner's journal → 403 HEALTH_ACCESS_DENIED")
    void impostorBlockedFromOwnerJournal() throws Exception {
        mockMvc.perform(get("/users/owner/health/journal")
                .header("Authorization", "Bearer " + impostorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("HEALTH_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Admin reading another user's health path → 403 (strict isolation, no bypass)")
    void adminDoesNotBypassHealthIsolation() throws Exception {
        mockMvc.perform(get("/users/owner/health/journal")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("HEALTH_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("No bearer token → 403 (Spring Security gate)")
    void unauthenticatedRequestBlocked() throws Exception {
        mockMvc.perform(get("/users/owner/health/journal"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Isolation holds across every resource under /users/{userId}/health/**")
    void isolationHoldsForAllResources() throws Exception {
        String[] paths = {
                "/users/owner/health/journal",
                "/users/owner/health/metrics",
                "/users/owner/health/documents",
                "/users/owner/health/appointments",
                "/users/owner/health/reminders"
        };
        for (String path : paths) {
            mockMvc.perform(get(Objects.requireNonNull(path)).header("Authorization", Objects.requireNonNull("Bearer " + impostorToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("HEALTH_ACCESS_DENIED"));
            mockMvc.perform(get(Objects.requireNonNull(path)).header("Authorization", Objects.requireNonNull("Bearer " + adminToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("HEALTH_ACCESS_DENIED"));
        }
    }
}
