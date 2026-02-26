package com.dylanjohnpratt.paradise.be.dto;

/**
 * Response body returned after a successful Plex upload.
 */
public record PlexUploadResponse(
    String fileName,
    String size
) {}
