package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.model.Notification;

import java.time.LocalDateTime;

/**
 * DTO for notification data returned to clients.
 * Contains all notification fields plus computed read state and action item availability.
 */
public record NotificationDTO(
    Long id,
    String subject,
    String messageBody,
    LocalDateTime createdAt,
    LocalDateTime expiresAt,
    boolean isGlobal,
    boolean isRead,
    boolean hasActionItem,
    ActionItemDTO actionItem,
    RecurrenceRuleDTO recurrenceRule
) {
    /**
     * Creates a NotificationDTO from a Notification entity with read state.
     * @param notification the entity to convert
     * @param isRead whether the notification has been read by the user
     * @return the DTO
     */
    public static NotificationDTO fromEntity(Notification notification, boolean isRead) {
        return new NotificationDTO(
            notification.getId(),
            notification.getSubject(),
            notification.getMessageBody(),
            notification.getCreatedAt(),
            notification.getExpiresAt(),
            notification.isGlobal(),
            isRead,
            notification.hasActionItem(),
            ActionItemDTO.fromEntity(notification.getActionItem()),
            RecurrenceRuleDTO.fromEntity(notification.getRecurrenceRule())
        );
    }
}
