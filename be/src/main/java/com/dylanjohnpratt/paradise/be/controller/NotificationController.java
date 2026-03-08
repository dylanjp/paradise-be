package com.dylanjohnpratt.paradise.be.controller;

import com.dylanjohnpratt.paradise.be.dto.CreateNotificationRequest;
import com.dylanjohnpratt.paradise.be.dto.NotificationDTO;
import com.dylanjohnpratt.paradise.be.exception.NotificationNotFoundException;
import com.dylanjohnpratt.paradise.be.model.ActionItem;
import com.dylanjohnpratt.paradise.be.model.Notification;
import com.dylanjohnpratt.paradise.be.model.RecurrenceRule;
import com.dylanjohnpratt.paradise.be.model.TodoTask;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.service.NotificationService;
import com.dylanjohnpratt.paradise.be.service.ProcessingResult;
import com.dylanjohnpratt.paradise.be.service.RecurringActionTodoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for notification management.
 * Provides endpoints for querying, creating, and managing notifications. Notifications
 * can be global (visible to all users) or targeted to specific users. Supports read/unread
 * state tracking per user, action item conversion to todo tasks, and admin-triggered
 * processing of recurring notifications. Domain exceptions are handled by
 * {@link com.dylanjohnpratt.paradise.be.exception.NotificationExceptionHandler}.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final RecurringActionTodoService recurringActionTodoService;

    public NotificationController(NotificationService notificationService,
                                  RecurringActionTodoService recurringActionTodoService) {
        this.notificationService = notificationService;
        this.recurringActionTodoService = recurringActionTodoService;
    }

    /**
     * Retrieves all notifications visible to the authenticated user.
     * Returns both global notifications and those specifically targeted to the user.
     * Each notification includes its read/unread state for the current user.
     *
     * @param currentUser    the currently authenticated user, injected by Spring Security
     * @param includeExpired whether to include expired notifications (defaults to false)
     * @return list of notifications with per-user read state
     */
    @GetMapping
    public ResponseEntity<List<NotificationDTO>> getNotifications(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "false") boolean includeExpired) {

        List<Notification> notifications = notificationService.getNotificationsForUser(
                currentUser.getId(), includeExpired);

        List<NotificationDTO> dtos = notifications.stream()
                .map(n -> NotificationDTO.fromEntity(n,
                        notificationService.isRead(n.getId(), currentUser.getId())))
                .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Retrieves a single notification by ID.
     * The notification must be visible to the authenticated user (global or targeted to them).
     *
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @param id          the notification ID
     * @return the notification with its read state, or 404 if not found/accessible
     */
    @GetMapping("/{id}")
    public ResponseEntity<NotificationDTO> getNotification(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {

        Notification notification = notificationService.getNotificationById(id, currentUser.getId())
                .orElseThrow(() -> new NotificationNotFoundException(id));

        boolean isRead = notificationService.isRead(id, currentUser.getId());
        return ResponseEntity.ok(NotificationDTO.fromEntity(notification, isRead));
    }

    /**
     * Creates a new notification. Requires ROLE_ADMIN.
     * Supports global notifications (visible to all), user-targeted notifications,
     * optional expiration, recurrence rules, and action items that users can convert to todos.
     *
     * @param request the notification creation request
     * @return the created notification as a DTO with HTTP 201 Created
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationDTO> createNotification(
            @RequestBody CreateNotificationRequest request) {

        RecurrenceRule recurrenceRule = request.recurrenceRule() != null
                ? request.recurrenceRule().toEntity()
                : null;

        ActionItem actionItem = request.actionItem() != null
                ? request.actionItem().toEntity()
                : null;

        Notification notification = notificationService.createNotification(
                request.subject(),
                request.messageBody(),
                request.isGlobal(),
                request.targetUserIds(),
                request.expiresAt(),
                recurrenceRule,
                actionItem
        );

        NotificationDTO dto = NotificationDTO.fromEntity(notification, false);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Marks a notification as read for the authenticated user.
     * Creates or updates the user's read state record for this notification.
     *
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @param id          the notification ID to mark as read
     * @return HTTP 204 No Content on success
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        notificationService.markAsRead(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Marks a notification as unread for the authenticated user.
     * Updates the user's read state record for this notification.
     *
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @param id          the notification ID to mark as unread
     * @return HTTP 204 No Content on success
     */
    @PostMapping("/{id}/unread")
    public ResponseEntity<Void> markAsUnread(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        notificationService.markAsUnread(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Converts a notification's action item into a todo task for the authenticated user.
     * The notification must have an action item, must not be expired, and the user
     * must not have already actioned it. The created todo task is returned.
     *
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @param id          the notification ID whose action item to convert
     * @return the created {@link TodoTask} with HTTP 201 Created
     */
    @PostMapping("/{id}/action")
    public ResponseEntity<TodoTask> convertToTodo(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        TodoTask task = notificationService.convertActionItemToTodo(
                id,
                currentUser.getId(),
                currentUser.getId().toString()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    /**
     * Permanently deletes a notification. Requires ROLE_ADMIN.
     * Also removes all associated user read states.
     *
     * @param id the notification ID to delete
     * @return HTTP 204 No Content on success
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Manually triggers processing of recurring notifications. Requires ROLE_ADMIN.
     * Evaluates all recurring notifications with action items, creates todo tasks
     * for target users whose notifications are due, and resets read states.
     * This is also invoked automatically by the scheduled recurring action todo processor.
     *
     * @return a {@link ProcessingResult} with counts of processed notifications, created todos, and any errors
     */
    @PostMapping("/process-recurring")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProcessingResult> processRecurringNotifications() {
        ProcessingResult result = recurringActionTodoService.processRecurringNotifications();
        return ResponseEntity.ok(result);
    }
}
