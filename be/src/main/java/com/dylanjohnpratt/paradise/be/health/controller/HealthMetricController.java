package com.dylanjohnpratt.paradise.be.health.controller;

import com.dylanjohnpratt.paradise.be.dto.HealthMetricPointRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthMetricRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthMetricResponse;
import com.dylanjohnpratt.paradise.be.dto.HealthMetricUpdateRequest;
import com.dylanjohnpratt.paradise.be.health.service.HealthMetricService;
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
 * REST endpoints for user-scoped health metrics (charts).
 */
@RestController
@RequestMapping("/users/{userId}/health/metrics")
public class HealthMetricController {

    private static final Logger log = LoggerFactory.getLogger(HealthMetricController.class);

    private final HealthMetricService metricService;

    public HealthMetricController(HealthMetricService metricService) {
        this.metricService = metricService;
    }

    @GetMapping
    public ResponseEntity<List<HealthMetricResponse>> list(
            @PathVariable String userId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(metricService.list(userId, currentUser));
    }

    @PostMapping
    public ResponseEntity<HealthMetricResponse> create(
            @PathVariable String userId,
            @Valid @RequestBody HealthMetricRequest request,
            @AuthenticationPrincipal User currentUser) {
        HealthMetricResponse response = metricService.create(userId, request, currentUser);
        log.info("AUDIT health.metric.create user={} targetUser={}", currentUser.getUsername(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{metricId}")
    public ResponseEntity<HealthMetricResponse> update(
            @PathVariable String userId,
            @PathVariable String metricId,
            @Valid @RequestBody HealthMetricUpdateRequest request,
            @AuthenticationPrincipal User currentUser) {
        HealthMetricResponse response = metricService.updateMetric(userId, metricId, request, currentUser);
        log.info("AUDIT health.metric.update user={} targetUser={} metricId={}",
                currentUser.getUsername(), userId, metricId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{metricId}")
    public ResponseEntity<Void> delete(
            @PathVariable String userId,
            @PathVariable String metricId,
            @AuthenticationPrincipal User currentUser) {
        metricService.delete(userId, metricId, currentUser);
        log.info("AUDIT health.metric.delete user={} targetUser={} metricId={}",
                currentUser.getUsername(), userId, metricId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{metricId}/points")
    public ResponseEntity<HealthMetricResponse> appendPoint(
            @PathVariable String userId,
            @PathVariable String metricId,
            @Valid @RequestBody HealthMetricPointRequest request,
            @AuthenticationPrincipal User currentUser) {
        HealthMetricResponse response = metricService.appendPoint(userId, metricId, request, currentUser);
        log.info("AUDIT health.metric.appendPoint user={} targetUser={} metricId={}",
                currentUser.getUsername(), userId, metricId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{metricId}/points/{index}")
    public ResponseEntity<HealthMetricResponse> updatePoint(
            @PathVariable String userId,
            @PathVariable String metricId,
            @PathVariable int index,
            @Valid @RequestBody HealthMetricPointRequest request,
            @AuthenticationPrincipal User currentUser) {
        HealthMetricResponse response = metricService.updatePoint(userId, metricId, index, request, currentUser);
        log.info("AUDIT health.metric.updatePoint user={} targetUser={} metricId={} index={}",
                currentUser.getUsername(), userId, metricId, index);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{metricId}/points/{index}")
    public ResponseEntity<HealthMetricResponse> deletePoint(
            @PathVariable String userId,
            @PathVariable String metricId,
            @PathVariable int index,
            @AuthenticationPrincipal User currentUser) {
        HealthMetricResponse response = metricService.deletePoint(userId, metricId, index, currentUser);
        log.info("AUDIT health.metric.deletePoint user={} targetUser={} metricId={} index={}",
                currentUser.getUsername(), userId, metricId, index);
        return ResponseEntity.ok(response);
    }
}
