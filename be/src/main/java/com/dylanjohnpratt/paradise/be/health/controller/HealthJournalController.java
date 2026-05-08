package com.dylanjohnpratt.paradise.be.health.controller;

import com.dylanjohnpratt.paradise.be.dto.HealthJournalEntryRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthJournalEntryResponse;
import com.dylanjohnpratt.paradise.be.health.service.HealthJournalService;
import com.dylanjohnpratt.paradise.be.model.User;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/**
 * REST endpoints for daily health journal entries.
 */
@RestController
@RequestMapping("/users/{userId}/health/journal")
public class HealthJournalController {

    private final HealthJournalService journalService;

    public HealthJournalController(HealthJournalService journalService) {
        this.journalService = journalService;
    }

    @GetMapping
    public ResponseEntity<List<HealthJournalEntryResponse>> list(
            @PathVariable String userId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(journalService.list(userId, currentUser));
    }

    @PostMapping
    public ResponseEntity<HealthJournalEntryResponse> upsert(
            @PathVariable String userId,
            @Valid @RequestBody HealthJournalEntryRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(journalService.upsert(userId, request, currentUser));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll(
            @PathVariable String userId,
            @AuthenticationPrincipal User currentUser) {
        journalService.deleteAll(userId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> delete(
            @PathVariable String userId,
            @PathVariable String entryId,
            @AuthenticationPrincipal User currentUser) {
        journalService.delete(userId, entryId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @PathVariable String userId,
            @AuthenticationPrincipal User currentUser) {
        StreamingResponseBody body = out -> journalService.exportCsv(userId, out, currentUser);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"health-journal.csv\"")
                .body(body);
    }
}
