package com.dylanjohnpratt.paradise.be.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Entity representing a notification that can be targeted to specific users or broadcast globally.
 * Supports optional recurrence rules, expiration, and action items that can be converted to TODO tasks.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String messageBody;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;  // null means never expires

    @Column(nullable = false)
    private boolean isGlobal;  // true = all users, false = specific users

    @ElementCollection
    @CollectionTable(name = "notification_target_users",
            joinColumns = @JoinColumn(name = "notification_id"))
    @Column(name = "user_id")
    private Set<Long> targetUserIds = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    private String recurrenceRuleJson;  // Serialized RecurrenceRule

    @Embedded
    private ActionItem actionItem;  // Optional embedded action item

    @Column(nullable = false)
    private boolean deleted = false;  // Soft delete flag

    public Notification() {
    }

    public Notification(String subject, String messageBody, boolean isGlobal) {
        this.subject = subject;
        this.messageBody = messageBody;
        this.isGlobal = isGlobal;
        this.createdAt = LocalDateTime.now();
    }

    public Notification(String subject, String messageBody, boolean isGlobal, 
                       LocalDateTime expiresAt, Set<Long> targetUserIds,
                       String recurrenceRuleJson, ActionItem actionItem) {
        this.subject = subject;
        this.messageBody = messageBody;
        this.isGlobal = isGlobal;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
        this.targetUserIds = targetUserIds != null ? new HashSet<>(targetUserIds) : new HashSet<>();
        this.recurrenceRuleJson = recurrenceRuleJson;
        this.actionItem = actionItem;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(String messageBody) {
        this.messageBody = messageBody;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public void setGlobal(boolean global) {
        isGlobal = global;
    }

    public Set<Long> getTargetUserIds() {
        return targetUserIds;
    }

    public void setTargetUserIds(Set<Long> targetUserIds) {
        this.targetUserIds = targetUserIds != null ? new HashSet<>(targetUserIds) : new HashSet<>();
    }

    public String getRecurrenceRuleJson() {
        return recurrenceRuleJson;
    }

    public void setRecurrenceRuleJson(String recurrenceRuleJson) {
        this.recurrenceRuleJson = recurrenceRuleJson;
    }

    /**
     * Gets the RecurrenceRule by deserializing from JSON.
     * @return the RecurrenceRule or null if not set
     */
    public RecurrenceRule getRecurrenceRule() {
        return RecurrenceRule.fromJson(recurrenceRuleJson);
    }

    /**
     * Sets the RecurrenceRule by serializing to JSON.
     * @param recurrenceRule the rule to set, or null to clear
     */
    public void setRecurrenceRule(RecurrenceRule recurrenceRule) {
        this.recurrenceRuleJson = recurrenceRule != null ? recurrenceRule.toJson() : null;
    }

    public ActionItem getActionItem() {
        return actionItem;
    }

    public void setActionItem(ActionItem actionItem) {
        this.actionItem = actionItem;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    /**
     * Checks if this notification has an action item defined.
     * @return true if an action item exists with a description
     */
    public boolean hasActionItem() {
        return actionItem != null && actionItem.getDescription() != null 
                && !actionItem.getDescription().isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Notification that = (Notification) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Notification{" +
                "id=" + id +
                ", subject='" + subject + '\'' +
                ", isGlobal=" + isGlobal +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                ", deleted=" + deleted +
                '}';
    }
}
