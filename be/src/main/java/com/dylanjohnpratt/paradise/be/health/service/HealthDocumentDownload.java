package com.dylanjohnpratt.paradise.be.health.service;

import com.dylanjohnpratt.paradise.be.health.model.HealthDocument;

import java.nio.file.Path;

/**
 * Lightweight holder for a verified document download — the on-disk {@link Path}
 * plus its metadata row. The controller consumes this to stream the file and
 * set response headers without re-reading the entity.
 */
public record HealthDocumentDownload(HealthDocument document, Path absolutePath) {}
