package com.dylanjohnpratt.paradise.be.dto;

/**
 * Request DTO for creating or updating a Daily task.
 * All fields are optional to support both create and partial update operations.
 */
public class DailyTaskRequest {
    
    private String id;
    private String description;
    private Boolean completed;
    private Integer order;

    public DailyTaskRequest() {
    }

    public DailyTaskRequest(String id, String description, Boolean completed, Integer order) {
        this.id = id;
        this.description = description;
        this.completed = completed;
        this.order = order;
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
}
