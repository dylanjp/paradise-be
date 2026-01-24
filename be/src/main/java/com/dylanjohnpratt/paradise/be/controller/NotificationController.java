package com.dylanjohnpratt.paradise.be.controller;

import com.dylanjohnpratt.paradise.be.dto.CreateNotificationRequest;
import com.dylanjohnpratt.paradise.be.dto.NotificationDTO;
import com.dylanjohnpratt.paradise.be.exception.*;
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
 * REST controller for notification management.
 * Provides endpoints for listing, viewing, creating, and managing notifications.
 * 
 * Requirements: All notification-related requirements
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
     * Get notifications for the current user.
     * Excludes expired notifications by default unless includeExpired=true.
     * 
     * @param currentUser the authenticated user
     * @param includeExpired whether to include expired notifications (for audit)
     * @return list of notifications visible to the user
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
     * Get a single notification by ID.
     * 
     * @param currentUser the authenticated user
     * @param id the notification ID
     * @return the notification if found and accessible
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
     * Create a new notification (admin only).
     * 
     * @param request the notification creation request
     * @return the created notification
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationDTO> createNotification(
            @RequestBody CreateNotificationRequest request) {
        
        // Convert DTOs to entities
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
        
        // New notifications are unread by default
        NotificationDTO dto = NotificationDTO.fromEntity(notification, false);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Mark a notification as read.
     * 
     * @param currentUser the authenticated user
     * @param id the notification ID
     * @return 204 No Content on success
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        
        try {
            notificationService.markAsRead(id, currentUser.getId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new NotificationNotFoundException(id);
        }
    }

    /**
     * Mark a notification as unread.
     * 
     * @param currentUser the authenticated user
     * @param id the notification ID
     * @return 204 No Content on success
     */
    @PostMapping("/{id}/unread")
    public ResponseEntity<Void> markAsUnread(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        
        try {
            notificationService.markAsUnread(id, currentUser.getId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new NotificationNotFoundException(id);
        }
    }

    /**
     * Convert a notification's action item to a TODO task.
     * 
     * @param currentUser the authenticated user
     * @param id the notification ID
     * @return the created TODO task
     */
    @PostMapping("/{id}/action")
    public ResponseEntity<TodoTask> convertToTodo(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        
        try {
            TodoTask task = notificationService.convertActionItemToTodo(
                    id, 
                    currentUser.getId(), 
                    currentUser.getId().toString()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(task);
        } catch (IllegalArgumentException e) {
            throw new NotificationNotFoundException(id);
        } catch (IllegalStateException e) {
            // Map IllegalStateException to appropriate custom exceptions
            String message = e.getMessage();
            if (message.contains("action item")) {
                throw new NoActionItemException();
            } else if (message.contains("expired")) {
                throw new NotificationExpiredException();
            } else if (message.contains("already")) {
                throw new DuplicateActionException();
            }
            throw e;
        }
    }

    /**
     * Soft delete a notification (admin only).
     * 
     * @param id the notification ID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        try {
            notificationService.deleteNotification(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new NotificationNotFoundException(id);
        }
    }

    /**
     * Manually trigger processing of recurring notifications with action items.
     * Creates TODO tasks for all due recurring notifications that haven't been processed today.
     * Admin only.
     * 
     * @return ProcessingResult with counts of processed notifications and created tasks
     */
    @PostMapping("/process-recurring")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProcessingResult> processRecurringNotifications() {
        ProcessingResult result = recurringActionTodoService.processRecurringNotifications();
        return ResponseEntity.ok(result);
    }
}
