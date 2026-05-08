package com.dylanjohnpratt.paradise.be.health.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A single day's health journal entry for a user.
 * Upserted by the unique pair (user_id, entry_date).
 */
@Entity
@Table(
        name = "health_journal_entries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_health_journal_user_date",
                columnNames = {"user_id", "entry_date"}
        ),
        indexes = {
                @Index(name = "idx_health_journal_user", columnList = "user_id"),
                @Index(name = "idx_health_journal_user_date", columnList = "user_id,entry_date")
        }
)
public class HealthJournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "weight_lbs", precision = 6, scale = 2)
    private BigDecimal weightLbs;

    @Column(name = "bed_time")
    private LocalTime bedTime;

    @Column(name = "wake_time")
    private LocalTime wakeTime;

    @Column(name = "energy", columnDefinition = "SMALLINT")
    private Short energy;

    @Column(name = "mood", columnDefinition = "SMALLINT")
    private Short mood;

    @Column(name = "thoughts", length = 10000)
    private String thoughts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public HealthJournalEntry() {
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public BigDecimal getWeightLbs() {
        return weightLbs;
    }

    public void setWeightLbs(BigDecimal weightLbs) {
        this.weightLbs = weightLbs;
    }

    public LocalTime getBedTime() {
        return bedTime;
    }

    public void setBedTime(LocalTime bedTime) {
        this.bedTime = bedTime;
    }

    public LocalTime getWakeTime() {
        return wakeTime;
    }

    public void setWakeTime(LocalTime wakeTime) {
        this.wakeTime = wakeTime;
    }

    public Short getEnergy() {
        return energy;
    }

    public void setEnergy(Short energy) {
        this.energy = energy;
    }

    public Short getMood() {
        return mood;
    }

    public void setMood(Short mood) {
        this.mood = mood;
    }

    public String getThoughts() {
        return thoughts;
    }

    public void setThoughts(String thoughts) {
        this.thoughts = thoughts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
