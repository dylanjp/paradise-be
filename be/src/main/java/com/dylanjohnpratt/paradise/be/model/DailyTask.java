package com.dylanjohnpratt.paradise.be.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a Daily task that includes a creation timestamp.
 * Daily tasks are user-scoped and organized as a flat list (no hierarchy).
 */
@Entity
@Table(name = "daily_tasks")
public class DailyTask {
    @Id
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String description;
    
    @Column(nullable = false)
    private boolean completed;
    
    @Column(name = "task_order", nullable = false)
    private int order;
    
    @Column(nullable = false)
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
