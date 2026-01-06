package com.dylanjohnpratt.paradise.be.model;

/**
 * Represents a persistent TODO task that belongs to a category and may have parent-child relationships.
 * TODO tasks are user-scoped and support hierarchical organization through the parentId field.
 */
public class TodoTask {
    private String id;
    private String userId;
    private String description;
    private String category;
    private boolean completed;
    private int order;
    private String parentId;  // null for root tasks

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
}
