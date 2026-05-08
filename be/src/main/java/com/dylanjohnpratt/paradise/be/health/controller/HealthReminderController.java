package com.dylanjohnpratt.paradise.be.health.controller;

import com.dylanjohnpratt.paradise.be.dto.HealthReminderPatchRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthReminderRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthReminderResponse;
import com.dylanjohnpratt.paradise.be.health.service.HealthReminderService;
import com.dylanjohnpratt.paradise.be.model.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for user-owned health reminders.
 */
@RestController
@RequestMapping("/users/{userId}/health/reminders")
public class HealthReminderController {

    private final HealthReminderService reminderService;

    public HealthReminderController(HealthReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    public ResponseEntity<List<HealthReminderResponse>> list(
            @PathVariable String userId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(reminderService.list(userId, currentUser));
    }

    @PostMapping
    public ResponseEntity<HealthReminderResponse> create(
            @PathVariable String userId,
            @Valid @RequestBody HealthReminderRequest request,
            @AuthenticationPrincipal User currentUser) {
        HealthReminderResponse response = reminderService.create(userId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{reminderId}")
    public ResponseEntity<HealthReminderResponse> patch(
            @PathVariable String userId,
            @PathVariable String reminderId,
            @Valid @RequestBody HealthReminderPatchRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(reminderService.patch(userId, reminderId, request, currentUser));
    }

    @DeleteMapping("/{reminderId}")
    public ResponseEntity<Void> delete(
            @PathVariable String userId,
            @PathVariable String reminderId,
            @AuthenticationPrincipal User currentUser) {
        reminderService.delete(userId, reminderId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
