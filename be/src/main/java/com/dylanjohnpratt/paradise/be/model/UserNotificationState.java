package com.dylanjohnpratt.paradise.be.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity tracking the read/unread state of a notification for a specific user.
 * Each user has at most one state record per notification.
 */
@Entity
@Table(name = "user_notification_states",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"notification_id", "user_id"}))
public class UserNotificationState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private boolean read = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime readAt;  // When marked as read

    public UserNotificationState() {
    }

    public UserNotificationState(Long notificationId, Long userId) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.read = false;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    /**
     * Marks this notification as read for the user.
     */
    public void markAsRead() {
        this.read = true;
        this.readAt = LocalDateTime.now();
    }

    /**
     * Marks this notification as unread for the user.
     */
    public void markAsUnread() {
        this.read = false;
        this.readAt = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserNotificationState that = (UserNotificationState) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UserNotificationState{" +
                "id=" + id +
                ", notificationId=" + notificationId +
                ", userId=" + userId +
                ", read=" + read +
                ", createdAt=" + createdAt +
                ", readAt=" + readAt +
                '}';
    }
}
