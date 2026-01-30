package com.dylanjohnpratt.paradise.be.repository;

import com.dylanjohnpratt.paradise.be.model.Notification;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.model.UserNotificationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for UserNotificationStateRepository.resetReadStateForNotification method.
 * 
 * Validates: Requirements 2.1, 2.3
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class UserNotificationStateRepositoryTest {

    @Autowired
    private UserNotificationStateRepository userNotificationStateRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user1;
    private User user2;
    private User user3;
    private Notification notification1;
    private Notification notification2;

    @BeforeEach
    void setUp() {
        // Clear existing data
        userNotificationStateRepository.deleteAll();
        notificationRepository.deleteAll();

        // Create test users with unique names
        String uniqueSuffix = String.valueOf(System.nanoTime());

        user1 = new User("stateuser1_" + uniqueSuffix,
                passwordEncoder.encode("pass1"), Set.of("ROLE_USER"));
        user1 = userRepository.save(user1);

        user2 = new User("stateuser2_" + uniqueSuffix,
                passwordEncoder.encode("pass2"), Set.of("ROLE_USER"));
        user2 = userRepository.save(user2);

        user3 = new User("stateuser3_" + uniqueSuffix,
                passwordEncoder.encode("pass3"), Set.of("ROLE_USER"));
        user3 = userRepository.save(user3);

        // Create test notifications
        notification1 = new Notification("Test Notification 1", "Body 1", true);
        notification1 = notificationRepository.save(notification1);

        notification2 = new Notification("Test Notification 2", "Body 2", true);
        notification2 = notificationRepository.save(notification2);
    }

    @Test
    @DisplayName("resetReadStateForNotification resets all matching records")
    void resetReadStateForNotification_resetsAllMatchingRecords() {
        // Arrange: Create read states for notification1 with read=true
        UserNotificationState state1 = new UserNotificationState(notification1.getId(), user1.getId());
        state1.markAsRead();
        userNotificationStateRepository.save(state1);

        UserNotificationState state2 = new UserNotificationState(notification1.getId(), user2.getId());
        state2.markAsRead();
        userNotificationStateRepository.save(state2);

        UserNotificationState state3 = new UserNotificationState(notification1.getId(), user3.getId());
        state3.markAsRead();
        userNotificationStateRepository.save(state3);

        // Verify all states are read before reset
        assertThat(userNotificationStateRepository.findByNotificationId(notification1.getId()))
                .allMatch(UserNotificationState::isRead);

        // Act: Reset read states for notification1
        int resetCount = userNotificationStateRepository.resetReadStateForNotification(notification1.getId());

        // Assert: All 3 records were reset
        assertThat(resetCount).isEqualTo(3);

        // Assert: All states are now unread with null readAt
        var resetStates = userNotificationStateRepository.findByNotificationId(notification1.getId());
        assertThat(resetStates).hasSize(3);
        assertThat(resetStates).allMatch(s -> !s.isRead());
        assertThat(resetStates).allMatch(s -> s.getReadAt() == null);
    }

    @Test
    @DisplayName("resetReadStateForNotification does not affect non-matching records")
    void resetReadStateForNotification_doesNotAffectNonMatchingRecords() {
        // Arrange: Create read states for both notifications
        UserNotificationState state1ForNotif1 = new UserNotificationState(notification1.getId(), user1.getId());
        state1ForNotif1.markAsRead();
        userNotificationStateRepository.save(state1ForNotif1);

        UserNotificationState state2ForNotif1 = new UserNotificationState(notification1.getId(), user2.getId());
        state2ForNotif1.markAsRead();
        userNotificationStateRepository.save(state2ForNotif1);

        // States for notification2 - these should NOT be affected
        UserNotificationState state1ForNotif2 = new UserNotificationState(notification2.getId(), user1.getId());
        state1ForNotif2.markAsRead();
        LocalDateTime notif2ReadAt = state1ForNotif2.getReadAt();
        userNotificationStateRepository.save(state1ForNotif2);

        UserNotificationState state2ForNotif2 = new UserNotificationState(notification2.getId(), user3.getId());
        state2ForNotif2.markAsRead();
        userNotificationStateRepository.save(state2ForNotif2);

        // Act: Reset read states for notification1 only
        int resetCount = userNotificationStateRepository.resetReadStateForNotification(notification1.getId());

        // Assert: Only 2 records (notification1) were reset
        assertThat(resetCount).isEqualTo(2);

        // Assert: notification1 states are reset
        var notif1States = userNotificationStateRepository.findByNotificationId(notification1.getId());
        assertThat(notif1States).hasSize(2);
        assertThat(notif1States).allMatch(s -> !s.isRead());
        assertThat(notif1States).allMatch(s -> s.getReadAt() == null);

        // Assert: notification2 states are NOT affected (still read)
        var notif2States = userNotificationStateRepository.findByNotificationId(notification2.getId());
        assertThat(notif2States).hasSize(2);
        assertThat(notif2States).allMatch(UserNotificationState::isRead);
        assertThat(notif2States).allMatch(s -> s.getReadAt() != null);
    }

    @Test
    @DisplayName("resetReadStateForNotification returns 0 when no matching records exist")
    void resetReadStateForNotification_returnsZeroWhenNoMatchingRecords() {
        // Arrange: No states exist for notification1

        // Act: Reset read states for notification1
        int resetCount = userNotificationStateRepository.resetReadStateForNotification(notification1.getId());

        // Assert: 0 records were reset
        assertThat(resetCount).isEqualTo(0);
    }

    @Test
    @DisplayName("resetReadStateForNotification handles already unread states")
    void resetReadStateForNotification_handlesAlreadyUnreadStates() {
        // Arrange: Create mixed states - some read, some unread
        UserNotificationState readState = new UserNotificationState(notification1.getId(), user1.getId());
        readState.markAsRead();
        userNotificationStateRepository.save(readState);

        UserNotificationState unreadState = new UserNotificationState(notification1.getId(), user2.getId());
        // Don't mark as read - stays unread
        userNotificationStateRepository.save(unreadState);

        // Act: Reset read states
        int resetCount = userNotificationStateRepository.resetReadStateForNotification(notification1.getId());

        // Assert: Both records were updated (even the already unread one)
        assertThat(resetCount).isEqualTo(2);

        // Assert: All states are unread
        var states = userNotificationStateRepository.findByNotificationId(notification1.getId());
        assertThat(states).hasSize(2);
        assertThat(states).allMatch(s -> !s.isRead());
        assertThat(states).allMatch(s -> s.getReadAt() == null);
    }
}
