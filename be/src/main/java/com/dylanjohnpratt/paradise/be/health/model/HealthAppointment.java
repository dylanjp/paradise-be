package com.dylanjohnpratt.paradise.be.health.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A medical appointment tracked by a user. {@link HealthAppointmentType#UPCOMING}
 * vs {@link HealthAppointmentType#VISITED} distinguishes scheduled from past visits.
 */
@Entity
@Table(
        name = "health_appointments",
        indexes = {
                @Index(name = "idx_health_appointment_user", columnList = "user_id"),
                @Index(name = "idx_health_appointment_user_date", columnList = "user_id,appt_date")
        }
)
public class HealthAppointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "doctor", nullable = false, length = 255)
    private String doctor;

    @Column(name = "specialty", length = 255)
    private String specialty;

    @Column(name = "appt_date", nullable = false)
    private LocalDateTime apptDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private HealthAppointmentType type;

    @Column(name = "notes", length = 4000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public HealthAppointment() {
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

    public String getDoctor() {
        return doctor;
    }

    public void setDoctor(String doctor) {
        this.doctor = doctor;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public LocalDateTime getApptDate() {
        return apptDate;
    }

    public void setApptDate(LocalDateTime apptDate) {
        this.apptDate = apptDate;
    }

    public HealthAppointmentType getType() {
        return type;
    }

    public void setType(HealthAppointmentType type) {
        this.type = type;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
