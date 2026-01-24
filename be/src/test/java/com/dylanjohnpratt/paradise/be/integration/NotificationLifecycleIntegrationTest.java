package com.dylanjohnpratt.paradise.be.integration;

import com.dylanjohnpratt.paradise.be.dto.ActionItemDTO;
import com.dylanjohnpratt.paradise.be.dto.CreateNotificationRequest;
import com.dylanjohnpratt.paradise.be.dto.LoginRequest;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.NotificationRepository;
import com.dylanjohnpratt.paradise.be.repository.TodoTaskRepository;
import com.dylanjohnpratt.paradise.be.repository.UserNotificationStateRepository;
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

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for notification lifecycle.
 * Tests the complete flow: create → read → mark as read → action → verify TODO.
 * 
 * Validates: All notification-related requirements
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class NotificationLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserNotificationStateRepository userNotificationStateRepository;

    @Autowired
    private TodoTaskRepository todoTaskRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String user1Token;
    private String user2Token;
    private Long adminId;
    private Long user1Id;
    private Long user2Id;
    private String user1Username;
    private String user2Username;

    @BeforeEach
    void setUp() throws Exception {
        // Clear existing notification data
        todoTaskRepository.deleteAll();
        userNotificationStateRepository.deleteAll();
        notificationRepository.deleteAll();

        // Use unique usernames to avoid conflicts with DataInitializer
        String uniqueSuffix = String.valueOf(System.nanoTime());
        user1Username = "notifuser1_" + uniqueSuffix;
        user2Username = "notifuser2_" + uniqueSuffix;
        
        // Create admin user (or reuse existing admin from DataInitializer)
        User admin = userRepository.findByUsername("admin")
                .orElseGet(() -> {
                    User newAdmin = new User("admin", passwordEncoder.encode("adminpass"), 
                            Set.of("ROLE_ADMIN", "ROLE_USER"));
                    return userRepository.save(newAdmin);
                });
        adminId = admin.getId();

        // Create regular users with unique names
        User user1 = new User(user1Username, passwordEncoder.encode("user1pass"), 
                Set.of("ROLE_USER"));
        user1 = userRepository.save(user1);
        user1Id = user1.getId();

        User user2 = new User(user2Username, passwordEncoder.encode("user2pass"), 
                Set.of("ROLE_USER"));
        user2 = userRepository.save(user2);
        user2Id = user2.getId();

        // Get tokens
        adminToken = obtainToken("admin", "adminpass");
        user1Token = obtainToken(user1Username, "user1pass");
        user2Token = obtainToken(user2Username, "user2pass");
    }

    private String obtainToken(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, password);
        
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(loginRequest))))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }


    @Test
    @DisplayName("Complete notification lifecycle: create → read → mark as read → action → verify TODO")
    void completeNotificationLifecycle() throws Exception {
        // Step 1: Admin creates a notification with action item targeting user1
        CreateNotificationRequest createRequest = new CreateNotificationRequest(
                "Complete your profile",
                "Please update your profile information to continue.",
                null, // no expiration
                false, // not global
                Set.of(user1Id),
                null, // no recurrence
                new ActionItemDTO("Update profile information", "Profile")
        );

        MvcResult createResult = mockMvc.perform(post("/api/notifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.subject").value("Complete your profile"))
                .andExpect(jsonPath("$.isRead").value(false))
                .andExpect(jsonPath("$.hasActionItem").value(true))
                .andReturn();

        Long notificationId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Step 2: User1 reads the notification (should be unread initially)
        mockMvc.perform(get("/api/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId))
                .andExpect(jsonPath("$.isRead").value(false));

        // Step 3: User1 marks the notification as read
        mockMvc.perform(post("/api/notifications/{id}/read", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        // Verify it's now marked as read
        mockMvc.perform(get("/api/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));

        // Step 4: User1 converts the action item to a TODO task
        MvcResult actionResult = mockMvc.perform(post("/api/notifications/{id}/action", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Update profile information"))
                .andExpect(jsonPath("$.category").value("Profile"))
                .andExpect(jsonPath("$.createdFromNotification").value(true))
                .andExpect(jsonPath("$.sourceNotificationId").value(notificationId))
                .andReturn();

        // Step 5: Verify duplicate action is prevented
        mockMvc.perform(post("/api/notifications/{id}/action", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Global notification broadcast to multiple users")
    void globalNotificationBroadcast() throws Exception {
        // Admin creates a global notification
        CreateNotificationRequest createRequest = new CreateNotificationRequest(
                "System Maintenance",
                "The system will be under maintenance tonight.",
                null,
                true, // global
                null, // no specific targets
                null,
                null
        );

        MvcResult createResult = mockMvc.perform(post("/api/notifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isGlobal").value(true))
                .andReturn();

        Long notificationId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // User1 can see the global notification
        mockMvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(notificationId))
                .andExpect(jsonPath("$[0].subject").value("System Maintenance"));

        // User2 can also see the global notification
        mockMvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(notificationId));

        // User1 marks as read - should not affect User2's read state
        mockMvc.perform(post("/api/notifications/{id}/read", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        // Verify User1 sees it as read
        mockMvc.perform(get("/api/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));

        // Verify User2 still sees it as unread
        mockMvc.perform(get("/api/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(false));
    }


    @Test
    @DisplayName("Expiration filtering behavior - expired notifications excluded by default")
    void expirationFilteringBehavior() throws Exception {
        // Create a notification that expires in the past (already expired)
        CreateNotificationRequest expiredRequest = new CreateNotificationRequest(
                "Expired Notice",
                "This notification has already expired.",
                LocalDateTime.now().minusDays(1), // expired yesterday
                false,
                Set.of(user1Id),
                null,
                null
        );

        MvcResult expiredResult = mockMvc.perform(post("/api/notifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(expiredRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        Long expiredNotificationId = objectMapper.readTree(expiredResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Create a non-expired notification
        CreateNotificationRequest activeRequest = new CreateNotificationRequest(
                "Active Notice",
                "This notification is still active.",
                LocalDateTime.now().plusDays(7), // expires in a week
                false,
                Set.of(user1Id),
                null,
                null
        );

        MvcResult activeResult = mockMvc.perform(post("/api/notifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(activeRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        Long activeNotificationId = objectMapper.readTree(activeResult.getResponse().getContentAsString())
                .get("id").asLong();

        // By default, expired notifications should be excluded
        mockMvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(activeNotificationId));

        // With includeExpired=true, both should be visible
        mockMvc.perform(get("/api/notifications")
                .param("includeExpired", "true")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("User-specific notification targeting - only targeted users can see")
    void userSpecificNotificationTargeting() throws Exception {
        // Create notification targeting only user1
        CreateNotificationRequest request = new CreateNotificationRequest(
                "Private Message",
                "This is only for user1.",
                null,
                false,
                Set.of(user1Id),
                null,
                null
        );

        MvcResult result = mockMvc.perform(post("/api/notifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long notificationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        // User1 can see the notification
        mockMvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(notificationId));

        // User2 cannot see the notification
        mockMvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // User2 cannot access the notification directly
        mockMvc.perform(get("/api/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Expired notification action prevention")
    void expiredNotificationActionPrevention() throws Exception {
        // Create an expired notification with action item
        CreateNotificationRequest request = new CreateNotificationRequest(
                "Expired Task",
                "This task has expired.",
                LocalDateTime.now().minusDays(1),
                false,
                Set.of(user1Id),
                null,
                new ActionItemDTO("Complete expired task", "Tasks")
        );

        MvcResult result = mockMvc.perform(post("/api/notifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long notificationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        // Attempting to action an expired notification should fail
        mockMvc.perform(post("/api/notifications/{id}/action", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Mark as read and unread toggle")
    void markAsReadUnreadToggle() throws Exception {
        // Create a notification
        CreateNotificationRequest request = new CreateNotificationRequest(
                "Toggle Test",
                "Testing read/unread toggle.",
                null,
                false,
                Set.of(user1Id),
                null,
                null
        );

        MvcResult result = mockMvc.perform(post("/api/notifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long notificationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        // Initially unread
        mockMvc.perform(get("/api/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(jsonPath("$.isRead").value(false));

        // Mark as read
        mockMvc.perform(post("/api/notifications/{id}/read", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(jsonPath("$.isRead").value(true));

        // Mark as unread
        mockMvc.perform(post("/api/notifications/{id}/unread", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(jsonPath("$.isRead").value(false));
    }

    @Test
    @DisplayName("Admin can soft delete notification")
    void adminCanSoftDeleteNotification() throws Exception {
        // Create a notification
        CreateNotificationRequest request = new CreateNotificationRequest(
                "To Be Deleted",
                "This notification will be deleted.",
                null,
                false,
                Set.of(user1Id),
                null,
                null
        );

        MvcResult result = mockMvc.perform(post("/api/notifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long notificationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        // User1 can see it initially
        mockMvc.perform(get("/api/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk());

        // Admin deletes it
        mockMvc.perform(delete("/api/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // User1 can no longer see it
        mockMvc.perform(get("/api/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Non-admin cannot create or delete notifications")
    void nonAdminCannotCreateOrDeleteNotifications() throws Exception {
        // Regular user cannot create notification
        CreateNotificationRequest request = new CreateNotificationRequest(
                "Unauthorized",
                "This should fail.",
                null,
                true,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/notifications")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // Create a notification as admin first
        MvcResult result = mockMvc.perform(post("/api/notifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long notificationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        // Regular user cannot delete notification
        mockMvc.perform(delete("/api/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isForbidden());
    }
}
