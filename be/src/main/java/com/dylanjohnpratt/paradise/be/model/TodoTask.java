package com.dylanjohnpratt.paradise.be.model;

import jakarta.persistence.*;

/**
 * Represents a persistent TODO task that belongs to a category and may have parent-child relationships.
 * TODO tasks are user-scoped and support hierarchical organization through the parentId field.
 */
@Entity
@Table(name = "todo_tasks")
public class TodoTask {
    @Id
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String description;
    
    private String category;
    
    @Column(nullable = false)
    private boolean completed;
    
    @Column(name = "task_order", nullable = false)
    private int order;
    
    private String parentId;  // null for root tasks
    
    private Boolean createdFromNotification;  // null treated as false for existing rows
    
    private Long sourceNotificationId;  // null if not created from notification

    public TodoTask() {
    }

    public TodoTask(String id, String userId, String description, String category, boolean completed, int order, String parentId) {
        this.id = id;
        this.userId = userId;
        this.description = description;
        this.category = category;
        this.completed = completed;
        this.order = order;
        this.parentId = parentId;
        this.createdFromNotification = false;
        this.sourceNotificationId = null;
    }
    
    public TodoTask(String id, String userId, String description, String category, boolean completed, int order, String parentId, boolean createdFromNotification, Long sourceNotificationId) {
        this.id = id;
        this.userId = userId;
        this.description = description;
        this.category = category;
        this.completed = completed;
        this.order = order;
        this.parentId = parentId;
        this.createdFromNotification = createdFromNotification;
        this.sourceNotificationId = sourceNotificationId;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
    
    public boolean isCreatedFromNotification() {
        return createdFromNotification != null && createdFromNotification;
    }
    
    public void setCreatedFromNotification(boolean createdFromNotification) {
        this.createdFromNotification = createdFromNotification;
    }
    
    public Long getSourceNotificationId() {
        return sourceNotificationId;
    }
    
    public void setSourceNotificationId(Long sourceNotificationId) {
        this.sourceNotificationId = sourceNotificationId;
    }
}
