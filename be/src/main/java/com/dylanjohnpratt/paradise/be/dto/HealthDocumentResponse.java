package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.health.model.HealthDocument;
import com.dylanjohnpratt.paradise.be.health.model.HealthDocumentCategory;

import java.time.LocalDateTime;

/**
 * Response payload for health document metadata (file bytes are fetched via
 * {@code GET /documents/{id}/download}).
 */
public record HealthDocumentResponse(
        String id,
        String name,
        HealthDocumentCategory category,
        String contentType,
        long sizeBytes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static HealthDocumentResponse from(HealthDocument doc) {
        return new HealthDocumentResponse(
                doc.getId(),
                doc.getName(),
                doc.getCategory(),
                doc.getContentType(),
                doc.getSizeBytes(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }
}
