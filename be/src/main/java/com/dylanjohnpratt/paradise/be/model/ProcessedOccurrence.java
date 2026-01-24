package com.dylanjohnpratt.paradise.be.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity tracking which recurring notification occurrences have been processed
 * to prevent duplicate TODO task creation.
 */
@Entity
@Table(name = "processed_occurrences",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_notification_occurrence",
           columnNames = {"notification_id", "occurrence_date"}))
public class ProcessedOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "todos_created", nullable = false)
    private int todosCreated;

    public ProcessedOccurrence() {
    }

    public ProcessedOccurrence(Long notificationId, LocalDate occurrenceDate, int todosCreated) {
        this.notificationId = notificationId;
        this.occurrenceDate = occurrenceDate;
        this.processedAt = LocalDateTime.now();
        this.todosCreated = todosCreated;
    }

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

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public void setOccurrenceDate(LocalDate occurrenceDate) {
        this.occurrenceDate = occurrenceDate;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public int getTodosCreated() {
        return todosCreated;
    }

    public void setTodosCreated(int todosCreated) {
        this.todosCreated = todosCreated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessedOccurrence that = (ProcessedOccurrence) o;
        return Objects.equals(notificationId, that.notificationId) &&
               Objects.equals(occurrenceDate, that.occurrenceDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(notificationId, occurrenceDate);
    }

    @Override
    public String toString() {
        return "ProcessedOccurrence{" +
                "id=" + id +
                ", notificationId=" + notificationId +
                ", occurrenceDate=" + occurrenceDate +
                ", processedAt=" + processedAt +
                ", todosCreated=" + todosCreated +
                '}';
    }
}
