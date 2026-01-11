package com.dylanjohnpratt.paradise.be.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Represents a completion record for a daily task.
 * Stores the date when a daily task was marked as complete.
 */
@Entity
@Table(name = "daily_task_completions", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"daily_task_id", "completion_date"}))
public class DailyTaskCompletion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "daily_task_id", nullable = false)
    private String dailyTaskId;
    
    @Column(name = "completion_date", nullable = false)
    private LocalDate completionDate;

    public DailyTaskCompletion() {
    }

    public DailyTaskCompletion(String dailyTaskId, LocalDate completionDate) {
        this.dailyTaskId = dailyTaskId;
        this.completionDate = completionDate;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDailyTaskId() {
        return dailyTaskId;
    }

    public void setDailyTaskId(String dailyTaskId) {
        this.dailyTaskId = dailyTaskId;
    }

    public LocalDate getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(LocalDate completionDate) {
        this.completionDate = completionDate;
    }
}
