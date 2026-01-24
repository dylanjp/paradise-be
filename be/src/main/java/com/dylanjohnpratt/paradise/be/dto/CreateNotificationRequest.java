package com.dylanjohnpratt.paradise.be.dto;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Request DTO for creating a new notification.
 * Supports both global and user-specific targeting, optional recurrence, and action items.
 */
public record CreateNotificationRequest(
    String subject,
    String messageBody,
    LocalDateTime expiresAt,
    boolean isGlobal,
    Set<Long> targetUserIds,
    RecurrenceRuleDTO recurrenceRule,
    ActionItemDTO actionItem
) {
}
