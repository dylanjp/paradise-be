package com.dylanjohnpratt.paradise.be.model;

import java.time.LocalDateTime;

/**
 * Represents a Daily task that includes a creation timestamp.
 * Daily tasks are user-scoped and organized as a flat list (no hierarchy).
 */
public class DailyTask {
    private String id;
    private String userId;
    private String description;
    private boolean completed;
    private int order;
    private LocalDateTime createdAt;

    public DailyTask() {
    }

    public DailyTask(String id, String userId, String description, boolean completed, int order, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.description = description;
        this.completed = completed;
        this.order = order;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
