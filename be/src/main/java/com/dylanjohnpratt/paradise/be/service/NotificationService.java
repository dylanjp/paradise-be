package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.model.ActionItem;
import com.dylanjohnpratt.paradise.be.model.Notification;
import com.dylanjohnpratt.paradise.be.model.RecurrenceRule;
import com.dylanjohnpratt.paradise.be.model.TodoTask;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.model.UserNotificationState;
import com.dylanjohnpratt.paradise.be.repository.NotificationRepository;
import com.dylanjohnpratt.paradise.be.repository.TodoTaskRepository;
import com.dylanjohnpratt.paradise.be.repository.UserNotificationStateRepository;
import com.dylanjohnpratt.paradise.be.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing notifications including creation, querying, and read state management.
 * Supports user-specific and global notifications with optional recurrence patterns.
 * 
 * Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.3, 3.6, 4.2, 4.3, 4.4, 4.5, 5.1, 5.4, 5.5, 6.2, 6.3, 6.4, 6.5, 6.6
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserNotificationStateRepository userNotificationStateRepository;
    private final TodoTaskRepository todoTaskRepository;
    private final UserRepository userRepository;
    private final RecurrenceService recurrenceService;

    public NotificationService(NotificationRepository notificationRepository,
                               UserNotificationStateRepository userNotificationStateRepository,
                               TodoTaskRepository todoTaskRepository,
                               UserRepository userRepository,
                               RecurrenceService recurrenceService) {
        this.notificationRepository = notificationRepository;
        this.userNotificationStateRepository = userNotificationStateRepository;
        this.todoTaskRepository = todoTaskRepository;
        this.userRepository = userRepository;
        this.recurrenceService = recurrenceService;
    }

    /**
     * Creates a new notification with the specified parameters.
     * Validates subject length, sets createdAt timestamp, and initializes random recurrence values if applicable.
     * 
     * @param subject the notification subject (max 255 characters)
     * @param messageBody the notification message body
     * @param isGlobal true for global notifications, false for user-specific
     * @param targetUserIds set of user IDs for user-specific notifications (ignored if isGlobal is true)
     * @param expiresAt optional expiration timestamp
     * @param recurrenceRule optional recurrence rule
     * @param actionItem optional action item
     * @return the created notification
     * @throws IllegalArgumentException if subject exceeds 255 characters or is empty
     * @throws IllegalArgumentException if messageBody is empty
     * @throws IllegalArgumentException if non-global notification has no target users
     * 
     * Requirements: 1.1, 1.2, 1.4, 2.1, 2.3, 3.6
     */
    @Transactional
    public Notification createNotification(String subject, String messageBody, boolean isGlobal,
                                           Set<Long> targetUserIds, LocalDateTime expiresAt,
                                           RecurrenceRule recurrenceRule,
                                           com.dylanjohnpratt.paradise.be.model.ActionItem actionItem) {
        // Validate subject length (Requirement 2.1)
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (subject.length() > 255) {
            throw new IllegalArgumentException("Subject must not exceed 255 characters");
        }

        // Validate message body
        if (messageBody == null || messageBody.isBlank()) {
            throw new IllegalArgumentException("Message body is required");
        }

        // Validate targeting (Requirements 1.1, 1.4)
        if (!isGlobal && (targetUserIds == null || targetUserIds.isEmpty())) {
            throw new IllegalArgumentException("Target user IDs required for non-global notifications");
        }

        // Initialize random recurrence values if applicable (Requirement 3.6)
        RecurrenceRule processedRule = recurrenceRule;
        if (recurrenceRule != null) {
            processedRule = recurrenceService.initializeRandomValues(recurrenceRule);
        }

        // Create notification with createdAt set automatically (Requirement 2.3)
        Notification notification = new Notification(
                subject,
                messageBody,
                isGlobal,
                expiresAt,
                isGlobal ? null : targetUserIds,
                processedRule != null ? processedRule.toJson() : null,
                actionItem
        );

        Notification savedNotification = notificationRepository.save(notification);

        // If notification has an action item but NO recurrence rule, create TODOs immediately
        if (actionItem != null && actionItem.getDescription() != null && !actionItem.getDescription().isBlank()
                && processedRule == null) {
            logger.info("Creating immediate TODOs for non-recurring notification {} with action item", 
                savedNotification.getId());
            createImmediateTodosForNotification(savedNotification);
        }

        return savedNotification;
    }

    /**
     * Creates TODO tasks immediately for all target users of a non-recurring notification.
     * For global notifications, creates TODOs for all active users.
     * For targeted notifications, creates TODOs for the specified target users.
     * 
     * @param notification the notification to create tasks from
     */
    private void createImmediateTodosForNotification(Notification notification) {
        Set<Long> targetUserIds = getTargetUserIds(notification);
        ActionItem actionItem = notification.getActionItem();
        
        logger.debug("Creating immediate TODOs for notification {} with {} target users", 
            notification.getId(), targetUserIds.size());
        
        int successCount = 0;
        
        for (Long userId : targetUserIds) {
            try {
                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    logger.warn("User {} not found, skipping TODO creation for notification {}", 
                        userId, notification.getId());
                    continue;
                }
                
                String username = user.getUsername();
                
                TodoTask task = new TodoTask(
                    UUID.randomUUID().toString(),
                    username,
                    actionItem.getDescription(),
                    actionItem.getCategory(),
                    false,
                    0,
                    null,
                    true,
                    notification.getId()
                );
                
                todoTaskRepository.save(task);
                successCount++;
                logger.debug("Created immediate TODO task for user {} from notification {}", 
                    username, notification.getId());
                
            } catch (Exception e) {
                logger.error("Failed to create immediate TODO for notification {} user {}: {}", 
                    notification.getId(), userId, e.getMessage(), e);
            }
        }
        
        logger.info("Created {} immediate TODO tasks for notification {}", successCount, notification.getId());
    }

    /**
     * Gets all user IDs that should receive a TODO from this notification.
     * For global notifications, returns all active user IDs.
     * For targeted notifications, returns the targetUserIds set.
     * 
     * @param notification the notification
     * @return set of user IDs to create tasks for
     */
    private Set<Long> getTargetUserIds(Notification notification) {
        if (notification.isGlobal()) {
            return userRepository.findAll().stream()
                .filter(User::isEnabled)
                .map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
        } else {
            return new HashSet<>(notification.getTargetUserIds());
        }
    }


    /**
     * Gets all notifications visible to a user, optionally including expired ones.
     * Creates UserNotificationState records for global notifications on first view.
     * 
     * @param userId the user ID
     * @param includeExpired true to include expired notifications (for audit purposes)
     * @return list of notifications visible to the user
     * 
     * Requirements: 1.2, 1.3, 5.1, 5.4, 5.5
     */
    @Transactional
    public List<Notification> getNotificationsForUser(Long userId, boolean includeExpired) {
        List<Notification> notifications;
        
        if (includeExpired) {
            // Include all notifications for audit purposes (Requirement 5.5)
            notifications = notificationRepository.findAllForUser(userId);
        } else {
            // Filter out expired notifications by default (Requirements 5.1, 5.4)
            notifications = notificationRepository.findNonExpiredForUser(userId, LocalDateTime.now());
        }

        // Create UserNotificationState for global notifications on first view (Requirement 1.3)
        for (Notification notification : notifications) {
            if (notification.isGlobal()) {
                ensureUserNotificationStateExists(notification.getId(), userId);
            }
        }

        return notifications;
    }

    /**
     * Gets a single notification by ID with access check.
     * Creates UserNotificationState for global notifications on first view.
     * 
     * @param notificationId the notification ID
     * @param userId the user ID requesting access
     * @return Optional containing the notification if found and accessible
     */
    @Transactional
    public Optional<Notification> getNotificationById(Long notificationId, Long userId) {
        Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
        
        if (notificationOpt.isEmpty()) {
            return Optional.empty();
        }

        Notification notification = notificationOpt.get();
        
        // Check if notification is deleted
        if (notification.isDeleted()) {
            return Optional.empty();
        }

        // Check access: user must be targeted or notification must be global
        if (!notification.isGlobal() && !notification.getTargetUserIds().contains(userId)) {
            return Optional.empty();
        }

        // Create UserNotificationState for global notifications on first view (Requirement 1.3)
        if (notification.isGlobal()) {
            ensureUserNotificationStateExists(notificationId, userId);
        }

        return Optional.of(notification);
    }

    /**
     * Marks a notification as read for a user.
     * Creates UserNotificationState if it doesn't exist.
     * 
     * @param notificationId the notification ID
     * @param userId the user ID
     * @throws IllegalArgumentException if notification not found or not accessible
     * 
     * Requirements: 4.2, 4.4
     */
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        // Verify notification exists and is accessible
        getNotificationById(notificationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found or not accessible"));

        UserNotificationState state = getOrCreateUserNotificationState(notificationId, userId);
        state.markAsRead();
        userNotificationStateRepository.save(state);
    }

    /**
     * Marks a notification as unread for a user.
     * Creates UserNotificationState if it doesn't exist.
     * 
     * @param notificationId the notification ID
     * @param userId the user ID
     * @throws IllegalArgumentException if notification not found or not accessible
     * 
     * Requirements: 4.3, 4.4
     */
    @Transactional
    public void markAsUnread(Long notificationId, Long userId) {
        // Verify notification exists and is accessible
        getNotificationById(notificationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found or not accessible"));

        UserNotificationState state = getOrCreateUserNotificationState(notificationId, userId);
        state.markAsUnread();
        userNotificationStateRepository.save(state);
    }

    /**
     * Gets the read state for a notification and user.
     * 
     * @param notificationId the notification ID
     * @param userId the user ID
     * @return true if read, false if unread or no state exists
     */
    public boolean isRead(Long notificationId, Long userId) {
        return userNotificationStateRepository.findByNotificationIdAndUserId(notificationId, userId)
                .map(UserNotificationState::isRead)
                .orElse(false);
    }

    /**
     * Checks if a notification is expired.
     * 
     * @param notification the notification to check
     * @return true if expired, false otherwise
     */
    public boolean isExpired(Notification notification) {
        if (notification.getExpiresAt() == null) {
            return false;
        }
        return notification.getExpiresAt().isBefore(LocalDateTime.now());
    }

    /**
     * Soft deletes a notification.
     * 
     * @param notificationId the notification ID
     * @throws IllegalArgumentException if notification not found
     */
    @Transactional
    public void deleteNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        
        notification.setDeleted(true);
        notificationRepository.save(notification);
    }

    /**
     * Converts a notification's action item to a TODO task for the specified user.
     * Validates that the notification has an action item, is not expired, and the user
     * hasn't already actioned it.
     * 
     * @param notificationId the notification ID
     * @param userId the user ID (as Long for notification access check)
     * @param userIdString the user ID as String (for TodoTask userId field)
     * @return the created TodoTask with notification provenance fields set
     * @throws IllegalArgumentException if notification not found or not accessible
     * @throws IllegalStateException if notification has no action item
     * @throws IllegalStateException if notification is expired
     * @throws IllegalStateException if user has already actioned this notification
     * 
     * Requirements: 6.2, 6.3, 6.4, 6.5, 6.6
     */
    @Transactional
    public TodoTask convertActionItemToTodo(Long notificationId, Long userId, String userIdString) {
        // Verify notification exists and is accessible
        Notification notification = getNotificationById(notificationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found or not accessible"));

        // Validate notification has action item (Requirement 6.2)
        if (!notification.hasActionItem()) {
            throw new IllegalStateException("This notification does not have an action item");
        }

        // Check notification not expired (Requirement 6.5)
        if (isExpired(notification)) {
            throw new IllegalStateException("Cannot action an expired notification");
        }

        // Check user hasn't already actioned (Requirement 6.6)
        if (hasUserActionedNotification(notificationId, userIdString)) {
            throw new IllegalStateException("You have already created a task from this notification");
        }

        // Create TodoTask with notification provenance fields (Requirements 6.3, 6.4)
        TodoTask task = new TodoTask(
                UUID.randomUUID().toString(),
                userIdString,
                notification.getActionItem().getDescription(),
                notification.getActionItem().getCategory(),
                false,
                0,
                null,
                true,  // createdFromNotification = true
                notificationId  // sourceNotificationId
        );

        return todoTaskRepository.save(task);
    }

    /**
     * Checks if a user has already created a TODO task from a specific notification.
     * 
     * @param notificationId the notification ID
     * @param userId the user ID (as String, matching TodoTask.userId)
     * @return true if the user has already actioned this notification
     * 
     * Requirement: 6.6
     */
    public boolean hasUserActionedNotification(Long notificationId, String userId) {
        return todoTaskRepository.existsByUserIdAndSourceNotificationId(userId, notificationId);
    }

    /**
     * Ensures a UserNotificationState exists for the given notification and user.
     * Creates one with default unread state if it doesn't exist.
     * 
     * @param notificationId the notification ID
     * @param userId the user ID
     */
    private void ensureUserNotificationStateExists(Long notificationId, Long userId) {
        if (!userNotificationStateRepository.existsByNotificationIdAndUserId(notificationId, userId)) {
            UserNotificationState state = new UserNotificationState(notificationId, userId);
            userNotificationStateRepository.save(state);
        }
    }

    /**
     * Gets or creates a UserNotificationState for the given notification and user.
     * 
     * @param notificationId the notification ID
     * @param userId the user ID
     * @return the existing or newly created state
     */
    private UserNotificationState getOrCreateUserNotificationState(Long notificationId, Long userId) {
        return userNotificationStateRepository.findByNotificationIdAndUserId(notificationId, userId)
                .orElseGet(() -> {
                    UserNotificationState state = new UserNotificationState(notificationId, userId);
                    return userNotificationStateRepository.save(state);
                });
    }
}
