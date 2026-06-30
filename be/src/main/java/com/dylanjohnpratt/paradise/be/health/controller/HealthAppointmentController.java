package com.dylanjohnpratt.paradise.be.health.controller;

import com.dylanjohnpratt.paradise.be.dto.HealthAppointmentPatchRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthAppointmentRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthAppointmentResponse;
import com.dylanjohnpratt.paradise.be.health.service.HealthAppointmentService;
import com.dylanjohnpratt.paradise.be.model.User;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for medical appointments.
 */
@RestController
@RequestMapping("/users/{userId}/health/appointments")
public class HealthAppointmentController {

    private static final Logger log = LoggerFactory.getLogger(HealthAppointmentController.class);

    private final HealthAppointmentService appointmentService;

    public HealthAppointmentController(HealthAppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public ResponseEntity<List<HealthAppointmentResponse>> list(
            @PathVariable String userId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(appointmentService.list(userId, currentUser));
    }

    @PostMapping
    public ResponseEntity<HealthAppointmentResponse> create(
            @PathVariable String userId,
            @Valid @RequestBody HealthAppointmentRequest request,
            @AuthenticationPrincipal User currentUser) {
        HealthAppointmentResponse response = appointmentService.create(userId, request, currentUser);
        log.info("AUDIT health.appointment.create user={} targetUser={}", currentUser.getUsername(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{appointmentId}")
    public ResponseEntity<HealthAppointmentResponse> patch(
            @PathVariable String userId,
            @PathVariable String appointmentId,
            @Valid @RequestBody HealthAppointmentPatchRequest request,
            @AuthenticationPrincipal User currentUser) {
        HealthAppointmentResponse response = appointmentService.patch(userId, appointmentId, request, currentUser);
        log.info("AUDIT health.appointment.patch user={} targetUser={} appointmentId={}",
                currentUser.getUsername(), userId, appointmentId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{appointmentId}")
    public ResponseEntity<Void> delete(
            @PathVariable String userId,
            @PathVariable String appointmentId,
            @AuthenticationPrincipal User currentUser) {
        appointmentService.delete(userId, appointmentId, currentUser);
        log.info("AUDIT health.appointment.delete user={} targetUser={} appointmentId={}",
                currentUser.getUsername(), userId, appointmentId);
        return ResponseEntity.noContent().build();
    }
}
