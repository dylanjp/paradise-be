package com.dylanjohnpratt.paradise.be.integration;

import com.dylanjohnpratt.paradise.be.model.ActionItem;
import com.dylanjohnpratt.paradise.be.model.Notification;
import com.dylanjohnpratt.paradise.be.model.RecurrenceRule;
import com.dylanjohnpratt.paradise.be.model.TodoTask;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.NotificationRepository;
import com.dylanjohnpratt.paradise.be.repository.OccurrenceTrackerRepository;
import com.dylanjohnpratt.paradise.be.repository.TodoTaskRepository;
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
 * Integration tests for RecurringActionTodoService.
 * Tests the complete flow of processing recurring notifications and creating TODOs.
 * 
 * Validates: Requirements 3.1, 3.4, 3.5
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class RecurringActionTodoIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    private User user1;
    private User user2;
    private User user3;

    @BeforeEach
    void setUp() {
        // Clear existing data
        todoTaskRepository.deleteAll();
        occurrenceTrackerRepository.deleteAll();
        notificationRepository.deleteAll();

        // Create test users with unique names
        String uniqueSuffix = String.valueOf(System.nanoTime());
        
        user1 = new User("recuruser1_" + uniqueSuffix, 
                passwordEncoder.encode("pass1"), Set.of("ROLE_USER"));
        user1 = userRepository.save(user1);

        user2 = new User("recuruser2_" + uniqueSuffix, 
                passwordEncoder.encode("pass2"), Set.of("ROLE_USER"));
        user2 = userRepository.save(user2);

        user3 = new User("recuruser3_" + uniqueSuffix, 
                passwordEncoder.encode("pass3"), Set.of("ROLE_USER"));
        user3 = userRepository.save(user3);
    }

    @Test
    @DisplayName("Happy path: recurring notification with action item creates TODOs for all target users")
    void recurringNotificationCreatesTodosForAllTargetUsers() {
        // Arrange: Create a daily recurring notification with action item targeting user1 and user2
        RecurrenceRule dailyRule = new RecurrenceRule(RecurrenceRule.RecurrenceType.DAILY, null, null);
        ActionItem actionItem = new ActionItem("Complete daily review", "Tasks");
        
        Notification notification = new Notification(
                "Daily Review Reminder",
                "Please complete your daily review.",
                false,  // not global
                null,   // no expiration
                Set.of(user1.getId(), user2.getId()),  // target user1 and user2
                dailyRule.toJson(),
                actionItem
        );
        notification = notificationRepository.save(notification);
        final Long notificationId = notification.getId();

        // Act: Process recurring notifications
        ProcessingResult result = recurringActionTodoService.processRecurringNotifications();

        // Assert: Verify processing result
        assertThat(result.notificationsProcessed()).isEqualTo(1);
        assertThat(result.todosCreated()).isEqualTo(2);  // One for each target user
        assertThat(result.errors()).isEqualTo(0);

        // Assert: Verify TODOs were created for user1
        List<TodoTask> user1Tasks = todoTaskRepository.findByUserId(user1.getId().toString());
        assertThat(user1Tasks).hasSize(1);
        TodoTask user1Task = user1Tasks.get(0);
        assertThat(user1Task.getDescription()).isEqualTo("Complete daily review");
        assertThat(user1Task.getCategory()).isEqualTo("Tasks");
        assertThat(user1Task.isCreatedFromNotification()).isTrue();
        assertThat(user1Task.getSourceNotificationId()).isEqualTo(notificationId);
        assertThat(user1Task.isCompleted()).isFalse();
        assertThat(user1Task.getOrder()).isEqualTo(0);
        assertThat(user1Task.getParentId()).isNull();

        // Assert: Verify TODOs were created for user2
        List<TodoTask> user2Tasks = todoTaskRepository.findByUserId(user2.getId().toString());
        assertThat(user2Tasks).hasSize(1);
        TodoTask user2Task = user2Tasks.get(0);
        assertThat(user2Task.getDescription()).isEqualTo("Complete daily review");
        assertThat(user2Task.getCategory()).isEqualTo("Tasks");
        assertThat(user2Task.isCreatedFromNotification()).isTrue();
        assertThat(user2Task.getSourceNotificationId()).isEqualTo(notificationId);

        // Assert: Verify NO TODOs were created for user3 (not targeted)
        List<TodoTask> user3Tasks = todoTaskRepository.findByUserId(user3.getId().toString());
        assertThat(user3Tasks).isEmpty();

        // Assert: Verify occurrence was tracked
        assertThat(occurrenceTrackerRepository.existsByNotificationIdAndOccurrenceDate(
                notificationId, java.time.LocalDate.now())).isTrue();
    }
}
