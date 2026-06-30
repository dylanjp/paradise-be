package com.dylanjohnpratt.paradise.be.health.controller;

import com.dylanjohnpratt.paradise.be.dto.HealthReminderPatchRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthReminderRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthReminderResponse;
import com.dylanjohnpratt.paradise.be.health.service.HealthReminderService;
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
 * REST endpoints for user-owned health reminders.
 */
@RestController
@RequestMapping("/users/{userId}/health/reminders")
public class HealthReminderController {

    private static final Logger log = LoggerFactory.getLogger(HealthReminderController.class);

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
        log.info("AUDIT health.reminder.create user={} targetUser={}", currentUser.getUsername(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{reminderId}")
    public ResponseEntity<HealthReminderResponse> patch(
            @PathVariable String userId,
            @PathVariable String reminderId,
            @Valid @RequestBody HealthReminderPatchRequest request,
            @AuthenticationPrincipal User currentUser) {
        HealthReminderResponse response = reminderService.patch(userId, reminderId, request, currentUser);
        log.info("AUDIT health.reminder.patch user={} targetUser={} reminderId={}",
                currentUser.getUsername(), userId, reminderId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{reminderId}")
    public ResponseEntity<Void> delete(
            @PathVariable String userId,
            @PathVariable String reminderId,
            @AuthenticationPrincipal User currentUser) {
        reminderService.delete(userId, reminderId, currentUser);
        log.info("AUDIT health.reminder.delete user={} targetUser={} reminderId={}",
                currentUser.getUsername(), userId, reminderId);
        return ResponseEntity.noContent().build();
    }
}
