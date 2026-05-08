package com.dylanjohnpratt.paradise.be.health.controller;

import com.dylanjohnpratt.paradise.be.dto.HealthAppointmentPatchRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthAppointmentRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthAppointmentResponse;
import com.dylanjohnpratt.paradise.be.health.service.HealthAppointmentService;
import com.dylanjohnpratt.paradise.be.model.User;
import jakarta.validation.Valid;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{appointmentId}")
    public ResponseEntity<HealthAppointmentResponse> patch(
            @PathVariable String userId,
            @PathVariable String appointmentId,
            @Valid @RequestBody HealthAppointmentPatchRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(appointmentService.patch(userId, appointmentId, request, currentUser));
    }

    @DeleteMapping("/{appointmentId}")
    public ResponseEntity<Void> delete(
            @PathVariable String userId,
            @PathVariable String appointmentId,
            @AuthenticationPrincipal User currentUser) {
        appointmentService.delete(userId, appointmentId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
