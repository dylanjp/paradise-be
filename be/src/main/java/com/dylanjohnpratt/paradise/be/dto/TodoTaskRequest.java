package com.dylanjohnpratt.paradise.be.dto;

/**
 * Request DTO for creating or updating a Todo task.
 * All fields are optional to support both create and partial update operations.
 */
public class TodoTaskRequest {
    
    private String id;
    private String description;
    private String category;
    private Boolean completed;
    private Integer order;
    private String parentId;

    public TodoTaskRequest() {
    }

    public TodoTaskRequest(String id, String description, String category, Boolean completed, Integer order, String parentId) {
        this.id = id;
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

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
}
