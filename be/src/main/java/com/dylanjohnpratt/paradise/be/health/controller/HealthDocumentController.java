package com.dylanjohnpratt.paradise.be.health.controller;

import com.dylanjohnpratt.paradise.be.dto.HealthDocumentResponse;
import com.dylanjohnpratt.paradise.be.health.model.HealthDocument;
import com.dylanjohnpratt.paradise.be.health.model.HealthDocumentCategory;
import com.dylanjohnpratt.paradise.be.health.service.HealthDocumentDownload;
import com.dylanjohnpratt.paradise.be.health.service.HealthDocumentService;
import com.dylanjohnpratt.paradise.be.model.User;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

/**
 * REST endpoints for health document uploads, downloads, and metadata. The
 * upload endpoint is multipart (file + category); everything else is JSON or
 * streamed bytes.
 */
@RestController
@RequestMapping("/users/{userId}/health/documents")
public class HealthDocumentController {

    private final HealthDocumentService documentService;

    public HealthDocumentController(HealthDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<List<HealthDocumentResponse>> list(
            @PathVariable String userId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(documentService.list(userId, currentUser));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HealthDocumentResponse> upload(
            @PathVariable String userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String categoryValue,
            @AuthenticationPrincipal User currentUser) {
        // Parse through the enum's @JsonCreator so clients can send either the
        // display name ("Lab Results") or the raw enum name ("LAB_RESULTS").
        HealthDocumentCategory category;
        try {
            category = HealthDocumentCategory.fromJson(categoryValue);
        } catch (IllegalArgumentException ex) {
            throw new com.dylanjohnpratt.paradise.be.exception.HealthValidationException(
                    "Unknown category: " + categoryValue);
        }
        HealthDocumentResponse response = documentService.upload(userId, file, category, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable String userId,
            @PathVariable String documentId,
            @AuthenticationPrincipal User currentUser) {
        HealthDocumentDownload download = documentService.resolveDownload(userId, documentId, currentUser);

        HealthDocument document = download.document();
        String contentType;
        try {
            String probed = Files.probeContentType(download.absolutePath());
            contentType = probed != null
                    ? probed
                    : (document.getContentType() != null ? document.getContentType() : "application/octet-stream");
        } catch (IOException e) {
            contentType = document.getContentType() != null ? document.getContentType() : "application/octet-stream";
        }

        String contentDisposition = ContentDisposition.attachment()
                .filename(document.getName())
                .build()
                .toString();

        StreamingResponseBody body = outputStream -> {
            try (InputStream in = Files.newInputStream(download.absolutePath())) {
                in.transferTo(outputStream);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(Objects.requireNonNull(contentType)))
                .contentLength(document.getSizeBytes())
                .header("Content-Disposition", contentDisposition)
                .body(body);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(
            @PathVariable String userId,
            @PathVariable String documentId,
            @AuthenticationPrincipal User currentUser) {
        documentService.delete(userId, documentId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @PathVariable String userId,
            @AuthenticationPrincipal User currentUser) {
        StreamingResponseBody body = out -> documentService.exportCsv(userId, out, currentUser);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"health-documents.csv\"")
                .body(body);
    }
}
