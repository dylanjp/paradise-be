package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.exception.OccurrenceTrackingException;
import com.dylanjohnpratt.paradise.be.model.ActionItem;
import com.dylanjohnpratt.paradise.be.model.Notification;
import com.dylanjohnpratt.paradise.be.model.ProcessedOccurrence;
import com.dylanjohnpratt.paradise.be.model.RecurrenceRule;
import com.dylanjohnpratt.paradise.be.model.TodoTask;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.NotificationRepository;
import com.dylanjohnpratt.paradise.be.repository.OccurrenceTrackerRepository;
import com.dylanjohnpratt.paradise.be.repository.TodoTaskRepository;
import com.dylanjohnpratt.paradise.be.repository.UserNotificationStateRepository;
import com.dylanjohnpratt.paradise.be.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for processing recurring notifications with action items and creating todo tasks.
 * Handles discovery of due notifications, creation of tasks for target users, and occurrence tracking.
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 5.1, 5.2, 5.3, 5.4
 */
@Service
public class RecurringActionTodoService {

    private static final Logger logger = LoggerFactory.getLogger(RecurringActionTodoService.class);

    private final NotificationRepository notificationRepository;
    private final OccurrenceTrackerRepository occurrenceTrackerRepository;
    private final TodoTaskRepository todoTaskRepository;
    private final RecurrenceService recurrenceService;
    private final UserRepository userRepository;
    private final UserNotificationStateRepository userNotificationStateRepository;

    private RecurringActionTodoService self;

    @Autowired
    @Lazy
    public void setSelf(RecurringActionTodoService self) {
        this.self = self;
    }

    public RecurringActionTodoService(
            NotificationRepository notificationRepository,
            OccurrenceTrackerRepository occurrenceTrackerRepository,
            TodoTaskRepository todoTaskRepository,
            RecurrenceService recurrenceService,
            UserRepository userRepository,
            UserNotificationStateRepository userNotificationStateRepository) {
        this.notificationRepository = notificationRepository;
        this.occurrenceTrackerRepository = occurrenceTrackerRepository;
        this.todoTaskRepository = todoTaskRepository;
        this.recurrenceService = recurrenceService;
        this.userRepository = userRepository;
        this.userNotificationStateRepository = userNotificationStateRepository;
    }

    /**
     * Processes all recurring notifications with action items that are due today.
     * Creates todo tasks for all targeted users and marks occurrences as processed.
     * Each notification is processed in its own transaction via processSingleNotification().
     * 
     * @return ProcessingResult containing counts of processed notifications and created tasks
     */
    @Transactional(readOnly = true)
    public ProcessingResult processRecurringNotifications() {
        LocalDate today = LocalDate.now();
        logger.info("Starting recurring action todo processing for date: {}", today);

        List<Notification> dueNotifications = findDueNotifications(today);
        logger.info("Found {} notifications due for processing", dueNotifications.size());

        ProcessingResult result = ProcessingResult.empty();

        for (Notification notification : dueNotifications) {
            try {
                logger.debug("Processing notification {} with subject '{}'", 
                    notification.getId(), notification.getSubject());
                
                // Use self-injection to invoke through Spring proxy for REQUIRES_NEW propagation.
                // Falls back to direct call when self is null (e.g., in unit tests without Spring context).
                int todosCreated;
                if (self != null) {
                    todosCreated = self.processSingleNotification(notification, today);
                } else {
                    todosCreated = processSingleNotification(notification, today);
                }
                
                result = result.addNotification(todosCreated);
                logger.info("Created {} todo tasks for notification {}", todosCreated, notification.getId());
                
            } catch (Exception e) {
                String errorMessage = "Failed to process notification " + notification.getId() + ": " + e.getMessage();
                logger.error(errorMessage, e);
                result = result.addError(errorMessage);
            }
        }

        logger.info("Completed recurring action todo processing. Notifications: {}, TODOs created: {}, Errors: {}",
            result.notificationsProcessed(), result.todosCreated(), result.errors());

        return result;
    }

    /**
     * Processes a single notification in its own transaction.
     * Creates todo tasks for all target users, resets read states, and marks the occurrence as processed.
     * 
     * @param notification the notification to process
     * @param today the current date for occurrence tracking
     * @return number of todos created
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processSingleNotification(Notification notification, LocalDate today) {
        int todosCreated = createTodosForNotification(notification, today);
        
        // Reset read states for all target users so notification appears as "new"
        resetReadStatesForNotification(notification);
        
        // Only mark as processed after successful todo creation
        markOccurrenceProcessed(notification.getId(), today, todosCreated);
        
        return todosCreated;
    }

    /**
     * Finds all recurring notifications with action items that are due on the given date
     * and have not yet been processed.
     * 
     * @param date the date to check for due notifications
     * @return list of notifications due for processing
     */
    public List<Notification> findDueNotifications(LocalDate date) {
        LocalDateTime now = LocalDateTime.now();
        
        // Query active recurring notifications with action items
        List<Notification> candidates = notificationRepository
            .findActiveRecurringNotificationsWithActionItems(now);
        
        // Filter by RecurrenceService.shouldDeliverOn() for the given date
        // and filter out already processed occurrences
        return candidates.stream()
            .filter(notification -> {
                RecurrenceRule rule = notification.getRecurrenceRule();
                if (rule == null) {
                    return false;
                }
                
                try {
                    // Check if notification should deliver on this date
                    boolean shouldDeliver = recurrenceService.shouldDeliverOn(rule, date, ZoneId.systemDefault());
                    if (!shouldDeliver) {
                        return false;
                    }
                    
                    // Check if occurrence has already been processed
                    return !isOccurrenceProcessed(notification.getId(), date);
                    
                } catch (Exception e) {
                    logger.warn("Error evaluating recurrence rule for notification {}: {}", 
                        notification.getId(), e.getMessage());
                    return false;
                }
            })
            .collect(Collectors.toList());
    }

    /**
     * Gets all user IDs that should receive a todo from this notification.
     * For global notifications, returns all active user IDs.
     * For targeted notifications, returns the targetUserIds set.
     * 
     * @param notification the notification
     * @return set of user IDs to create tasks for
     */
    public Set<Long> getTargetUserIds(Notification notification) {
        if (notification.isGlobal()) {
            // For global notifications: query all active user IDs
            return userRepository.findAll().stream()
                .filter(User::isEnabled)
                .map(User::getId)
                .collect(Collectors.toSet());
        } else {
            // For targeted notifications: return notification.getTargetUserIds()
            return new HashSet<>(notification.getTargetUserIds());
        }
    }

    /**
     * Creates todo tasks for all target users of a notification.
     * Handles individual user failures gracefully.
     * 
     * @param notification the notification to create tasks from
     * @param occurrenceDate the occurrence date for tracking
     * @return number of tasks created
     */
    public int createTodosForNotification(Notification notification, LocalDate occurrenceDate) {
        Set<Long> targetUserIds = getTargetUserIds(notification);
        ActionItem actionItem = notification.getActionItem();
        
        if (actionItem == null || actionItem.getDescription() == null || actionItem.getDescription().isBlank()) {
            logger.warn("Notification {} has no valid action item, skipping todo creation", notification.getId());
            return 0;
        }
        
        logger.debug("Creating TODOs for notification {} with {} target users", 
            notification.getId(), targetUserIds.size());
        
        int successCount = 0;
        
        for (Long userId : targetUserIds) {
            try {
                User user = userRepository.findById(Objects.requireNonNull(userId)).orElse(null);
                if (user == null) {
                    logger.warn("User {} not found, skipping todo creation for notification {}", 
                        userId, notification.getId());
                    continue;
                }
                
                String username = user.getUsername();
                
                TodoTask task = new TodoTask(
                    UUID.randomUUID().toString(),  // Unique ID
                    username,                       // Use username, not numeric ID
                    actionItem.getDescription(),    // Copy description from action item
                    actionItem.getCategory(),       // Copy category from action item
                    false,                          // Not completed
                    0,                              // Order 0
                    null,                           // No parent
                    true,                           // Created from notification
                    notification.getId()            // Source notification ID
                );
                
                todoTaskRepository.saveAndFlush(task);
                successCount++;
                logger.debug("Created todo task for user {} from notification {}", username, notification.getId());
                
            } catch (Exception e) {
                // Handle individual user failures gracefully - log and continue
                logger.error("Failed to create todo for notification {} user {}: {}", 
                    notification.getId(), userId, e.getMessage(), e);
            }
        }
        
        return successCount;
    }

    /**
     * Checks if an occurrence has already been processed.
     * 
     * @param notificationId the notification ID
     * @param occurrenceDate the occurrence date
     * @return true if already processed
     */
    public boolean isOccurrenceProcessed(Long notificationId, LocalDate occurrenceDate) {
        return occurrenceTrackerRepository.existsByNotificationIdAndOccurrenceDate(notificationId, occurrenceDate);
    }

    /**
     * Marks an occurrence as processed.
     * 
     * @param notificationId the notification ID
     * @param occurrenceDate the occurrence date
     * @param todosCreated the number of TODOs created for this occurrence
     */
    public void markOccurrenceProcessed(Long notificationId, LocalDate occurrenceDate, int todosCreated) {
        try {
            ProcessedOccurrence occurrence = new ProcessedOccurrence(notificationId, occurrenceDate, todosCreated);
            occurrenceTrackerRepository.save(occurrence);
            logger.debug("Marked occurrence as processed: notification={}, date={}, todosCreated={}", 
                notificationId, occurrenceDate, todosCreated);
        } catch (Exception e) {
            throw new OccurrenceTrackingException(notificationId, occurrenceDate, e);
        }
    }

    /**
     * Resets read state for all target users of a notification.
     * For global notifications: resets all existing UserNotificationState records.
     * For targeted notifications: resets states for target users only.
     * 
     * This method handles failures gracefully - if the reset fails, it logs the error
     * and continues processing (non-blocking).
     * 
     * @param notification the notification to reset read states for
     * Requirements: 1.1, 1.2, 3.2
     */
    public void resetReadStatesForNotification(Notification notification) {
        try {
            int resetCount = userNotificationStateRepository.resetReadStateForNotification(notification.getId());
            logger.debug("Reset read state for {} users on notification {}", resetCount, notification.getId());
        } catch (Exception e) {
            logger.warn("Failed to reset read states for notification {}: {}", 
                notification.getId(), e.getMessage(), e);
            // Non-blocking: log and continue processing
        }
    }
}
