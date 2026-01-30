package com.dylanjohnpratt.paradise.be.integration;

import com.dylanjohnpratt.paradise.be.model.ActionItem;
import com.dylanjohnpratt.paradise.be.model.Notification;
import com.dylanjohnpratt.paradise.be.model.RecurrenceRule;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.model.UserNotificationState;
import com.dylanjohnpratt.paradise.be.repository.NotificationRepository;
import com.dylanjohnpratt.paradise.be.repository.OccurrenceTrackerRepository;
import com.dylanjohnpratt.paradise.be.repository.TodoTaskRepository;
import com.dylanjohnpratt.paradise.be.repository.UserNotificationStateRepository;
import com.dylanjohnpratt.paradise.be.repository.UserRepository;
import com.dylanjohnpratt.paradise.be.service.ProcessingResult;
import com.dylanjohnpratt.paradise.be.service.RecurringActionTodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for recurring notification read state reset functionality.
 * Tests that processRecurringNotifications resets read states for target users.
 * 
 * Validates: Requirements 3.1, 3.2
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class RecurringNotificationReadResetIntegrationTest {

    @Autowired
    private RecurringActionTodoService recurringActionTodoService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TodoTaskRepository todoTaskRepository;

    @Autowired
    private OccurrenceTrackerRepository occurrenceTrackerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserNotificationStateRepository userNotificationStateRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user1;
    private User user2;
    private User user3;

    @BeforeEach
    void setUp() {
        // Clear existing data
        todoTaskRepository.deleteAll();
        occurrenceTrackerRepository.deleteAll();
        userNotificationStateRepository.deleteAll();
        notificationRepository.deleteAll();

        // Create test users with unique names
        String uniqueSuffix = String.valueOf(System.nanoTime());

        user1 = new User("resetuser1_" + uniqueSuffix,
                passwordEncoder.encode("pass1"), Set.of("ROLE_USER"));
        user1 = userRepository.save(user1);

        user2 = new User("resetuser2_" + uniqueSuffix,
                passwordEncoder.encode("pass2"), Set.of("ROLE_USER"));
        user2 = userRepository.save(user2);

        user3 = new User("resetuser3_" + uniqueSuffix,
                passwordEncoder.encode("pass3"), Set.of("ROLE_USER"));
        user3 = userRepository.save(user3);
    }

    @Test
    @DisplayName("processRecurringNotifications resets read states for all users with existing states")
    void processRecurringNotifications_resetsReadStates() {
        // Arrange: Create a daily recurring notification with action item
        RecurrenceRule dailyRule = new RecurrenceRule(RecurrenceRule.RecurrenceType.DAILY, null, null);
        ActionItem actionItem = new ActionItem("Complete daily task", "Tasks");

        Notification notification = new Notification(
                "Daily Reminder",
                "Please complete your daily task.",
                false,  // not global
                null,   // no expiration
                Set.of(user1.getId(), user2.getId()),  // target user1 and user2
                dailyRule.toJson(),
                actionItem
        );
        notification = notificationRepository.save(notification);
        final Long notificationId = notification.getId();

        // Create read states for users (simulating they read the notification previously)
        UserNotificationState state1 = new UserNotificationState(notificationId, user1.getId());
        state1.markAsRead();
        userNotificationStateRepository.save(state1);

        UserNotificationState state2 = new UserNotificationState(notificationId, user2.getId());
        state2.markAsRead();
        userNotificationStateRepository.save(state2);

        // Verify states are read before processing
        assertThat(userNotificationStateRepository.findByNotificationId(notificationId))
                .allMatch(UserNotificationState::isRead);

        // Act: Process recurring notifications
        ProcessingResult result = recurringActionTodoService.processRecurringNotifications();

        // Assert: Processing succeeded
        assertThat(result.notificationsProcessed()).isEqualTo(1);
        assertThat(result.todosCreated()).isEqualTo(2);
        assertThat(result.errors()).isEqualTo(0);

        // Assert: Read states were reset
        List<UserNotificationState> states = userNotificationStateRepository.findByNotificationId(notificationId);
        assertThat(states).hasSize(2);
        assertThat(states).allMatch(s -> !s.isRead());
        assertThat(states).allMatch(s -> s.getReadAt() == null);
    }

    @Test
    @DisplayName("processRecurringNotifications continues processing when no read states exist")
    void processRecurringNotifications_continuesWhenNoReadStatesExist() {
        // Arrange: Create a daily recurring notification with action item
        RecurrenceRule dailyRule = new RecurrenceRule(RecurrenceRule.RecurrenceType.DAILY, null, null);
        ActionItem actionItem = new ActionItem("Complete daily task", "Tasks");

        Notification notification = new Notification(
                "Daily Reminder",
                "Please complete your daily task.",
                false,  // not global
                null,   // no expiration
                Set.of(user1.getId(), user2.getId()),  // target user1 and user2
                dailyRule.toJson(),
                actionItem
        );
        notification = notificationRepository.save(notification);

        // No read states exist - this is a fresh notification

        // Act: Process recurring notifications
        ProcessingResult result = recurringActionTodoService.processRecurringNotifications();

        // Assert: Processing succeeded even without existing read states
        assertThat(result.notificationsProcessed()).isEqualTo(1);
        assertThat(result.todosCreated()).isEqualTo(2);
        assertThat(result.errors()).isEqualTo(0);
    }

    @Test
    @DisplayName("processRecurringNotifications resets states for global notification")
    void processRecurringNotifications_resetsStatesForGlobalNotification() {
        // Arrange: Create a global daily recurring notification
        RecurrenceRule dailyRule = new RecurrenceRule(RecurrenceRule.RecurrenceType.DAILY, null, null);
        ActionItem actionItem = new ActionItem("Global daily task", "Tasks");

        Notification notification = new Notification(
                "Global Daily Reminder",
                "Please complete your daily task.",
                true,   // global
                null,   // no expiration
                Set.of(),  // no specific targets (global)
                dailyRule.toJson(),
                actionItem
        );
        notification = notificationRepository.save(notification);
        final Long notificationId = notification.getId();

        // Create read states for all users
        UserNotificationState state1 = new UserNotificationState(notificationId, user1.getId());
        state1.markAsRead();
        userNotificationStateRepository.save(state1);

        UserNotificationState state2 = new UserNotificationState(notificationId, user2.getId());
        state2.markAsRead();
        userNotificationStateRepository.save(state2);

        UserNotificationState state3 = new UserNotificationState(notificationId, user3.getId());
        state3.markAsRead();
        userNotificationStateRepository.save(state3);

        // Act: Process recurring notifications
        ProcessingResult result = recurringActionTodoService.processRecurringNotifications();

        // Assert: Processing succeeded
        assertThat(result.notificationsProcessed()).isEqualTo(1);
        assertThat(result.errors()).isEqualTo(0);

        // Assert: All read states were reset
        List<UserNotificationState> states = userNotificationStateRepository.findByNotificationId(notificationId);
        assertThat(states).hasSize(3);
        assertThat(states).allMatch(s -> !s.isRead());
        assertThat(states).allMatch(s -> s.getReadAt() == null);
    }

    @Test
    @DisplayName("resetReadStatesForNotification handles non-existent notification gracefully (non-blocking)")
    void resetReadStatesForNotification_handlesNonExistentNotificationGracefully() {
        // Arrange: Create a notification that we'll use to verify processing continues
        RecurrenceRule dailyRule = new RecurrenceRule(RecurrenceRule.RecurrenceType.DAILY, null, null);
        ActionItem actionItem = new ActionItem("Test task", "Tasks");

        Notification notification = new Notification(
                "Test Notification",
                "Test message",
                false,
                null,
                Set.of(user1.getId()),
                dailyRule.toJson(),
                actionItem
        );
        notification = notificationRepository.save(notification);

        // Create a read state for user1
        UserNotificationState state = new UserNotificationState(notification.getId(), user1.getId());
        state.markAsRead();
        userNotificationStateRepository.save(state);

        // Act: Call resetReadStatesForNotification with a non-existent notification ID
        // This should not throw an exception (non-blocking behavior per Requirement 3.2)
        Notification fakeNotification = new Notification(
                "Fake",
                "Fake",
                false,
                null,
                Set.of(),
                null,
                null
        );
        // Use reflection or direct call - the method should handle gracefully
        recurringActionTodoService.resetReadStatesForNotification(fakeNotification);

        // Assert: No exception was thrown and original state is unchanged
        // (since we called reset on a different notification)
        UserNotificationState unchangedState = userNotificationStateRepository
                .findByNotificationIdAndUserId(notification.getId(), user1.getId())
                .orElseThrow();
        assertThat(unchangedState.isRead()).isTrue();
    }

    @Test
    @DisplayName("processRecurringNotifications creates TODOs and marks occurrence even when reset affects zero records")
    void processRecurringNotifications_completesProcessingWhenResetAffectsZeroRecords() {
        // Arrange: Create a notification where no users have read states yet
        // This tests that processing completes successfully even when reset returns 0
        RecurrenceRule dailyRule = new RecurrenceRule(RecurrenceRule.RecurrenceType.DAILY, null, null);
        ActionItem actionItem = new ActionItem("Fresh notification task", "Tasks");

        Notification notification = new Notification(
                "Fresh Daily Reminder",
                "This is a brand new notification",
                false,
                null,
                Set.of(user1.getId(), user2.getId()),
                dailyRule.toJson(),
                actionItem
        );
        notification = notificationRepository.save(notification);
        final Long notificationId = notification.getId();

        // No read states exist - reset will affect 0 records

        // Act: Process recurring notifications
        ProcessingResult result = recurringActionTodoService.processRecurringNotifications();

        // Assert: Processing completed successfully - this is the key assertion
        // The service should complete without errors even when reset affects 0 records
        assertThat(result.notificationsProcessed()).isEqualTo(1);
        assertThat(result.todosCreated()).isEqualTo(2);
        assertThat(result.errors()).isEqualTo(0);

        // Assert: Occurrence was marked as processed (proves full processing completed)
        assertThat(occurrenceTrackerRepository.existsByNotificationIdAndOccurrenceDate(
                notificationId, java.time.LocalDate.now())).isTrue();
    }
}
