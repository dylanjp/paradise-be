package com.dylanjohnpratt.paradise.be.health.service;

import com.dylanjohnpratt.paradise.be.dto.HealthDocumentResponse;
import com.dylanjohnpratt.paradise.be.exception.HealthAccessDeniedException;
import com.dylanjohnpratt.paradise.be.exception.HealthDocumentTooLargeException;
import com.dylanjohnpratt.paradise.be.exception.HealthDocumentUnsupportedException;
import com.dylanjohnpratt.paradise.be.exception.HealthNotFoundException;
import com.dylanjohnpratt.paradise.be.exception.HealthValidationException;
import com.dylanjohnpratt.paradise.be.health.config.HealthStorageProperties;
import com.dylanjohnpratt.paradise.be.health.model.HealthDocument;
import com.dylanjohnpratt.paradise.be.health.model.HealthDocumentCategory;
import com.dylanjohnpratt.paradise.be.health.repository.HealthDocumentRepository;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.util.ByteSizes;
import com.dylanjohnpratt.paradise.be.util.FileNameSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Business logic for health document uploads, metadata, download streaming,
 * and deletion. File bytes live on disk under
 * {@link HealthStorageProperties#documents()}; metadata lives in
 * {@code health_documents}.
 */
@Service
public class HealthDocumentService {

    private static final Logger log = LoggerFactory.getLogger(HealthDocumentService.class);

    /**
     * Declared content-type to file-extension allow-list. Either side of the pair
     * (content type or extension) is sufficient — this lets clients with permissive
     * MIME detection still upload.
     */
    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
            "application/pdf", Set.of("pdf"),
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx"),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("xlsx")
    );

    private final HealthDocumentRepository documentRepository;
    private final HealthStorageProperties storageProperties;

    public HealthDocumentService(
            HealthDocumentRepository documentRepository,
            HealthStorageProperties storageProperties) {
        this.documentRepository = documentRepository;
        this.storageProperties = storageProperties;
    }

    @Transactional(readOnly = true)
    public List<HealthDocumentResponse> list(String userId, User currentUser) {
        checkAccess(userId, currentUser);
        return documentRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(HealthDocumentResponse::from)
                .toList();
    }

    @Transactional
    public HealthDocumentResponse upload(
            String userId,
            MultipartFile file,
            HealthDocumentCategory category,
            User currentUser) {
        checkAccess(userId, currentUser);

        // 1. null/empty check
        if (file == null || file.isEmpty()) {
            throw new HealthValidationException("file is required");
        }
        if (category == null) {
            throw new HealthValidationException("category is required");
        }

        // 2. size check (non-positive maxBytes means unlimited)
        long maxBytes = storageProperties.maxBytes();
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            throw new HealthDocumentTooLargeException(
                    "File exceeds maximum size of " + ByteSizes.format(maxBytes));
        }

        // 3. MIME / extension allowlist
        String originalName = file.getOriginalFilename();
        String declaredType = file.getContentType();
        if (!isAllowed(declaredType, originalName)) {
            throw new HealthDocumentUnsupportedException(
                    "Unsupported file type. Allowed: pdf, jpg, jpeg, png, docx, xlsx");
        }

        // 4. sanitize filename
        String sanitized;
        try {
            sanitized = FileNameSanitizer.sanitize(originalName);
        } catch (IllegalArgumentException e) {
            throw new HealthValidationException(e.getMessage());
        }

        // 5. persist entity FIRST to get the generated UUID, then compute path
        HealthDocument document = new HealthDocument();
        document.setUserId(currentUser.getId());
        document.setName(sanitized);
        document.setCategory(category);
        document.setContentType(declaredType != null ? declaredType : "application/octet-stream");
        document.setSizeBytes(file.getSize());
        // placeholder; will be updated after we know the id
        document.setStoragePath("pending");
        HealthDocument saved = documentRepository.save(document);
        documentRepository.flush();

        String relativePath = currentUser.getId() + "/" + saved.getId() + "/" + sanitized;
        Path storageRoot = Paths.get(storageProperties.documents()).toAbsolutePath().normalize();
        Path absolute = storageRoot.resolve(relativePath).normalize();

        // Defense-in-depth path-traversal guard.
        if (!absolute.startsWith(storageRoot)) {
            documentRepository.delete(saved);
            throw new HealthValidationException("Invalid file path");
        }

        try {
            Files.createDirectories(absolute.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, absolute);
            }
        } catch (IOException e) {
            documentRepository.delete(saved);
            log.warn("Failed to store health document for user {}: {}", currentUser.getId(), e.toString());
            throw new HealthValidationException("Failed to store document");
        }

        saved.setStoragePath(relativePath);
        HealthDocument persisted = documentRepository.save(saved);
        return HealthDocumentResponse.from(persisted);
    }

    /**
     * Looks up the document metadata, verifies the file is still reachable within
     * the storage root, and returns both for the controller to stream.
     */
    @Transactional(readOnly = true)
    public HealthDocumentDownload resolveDownload(String userId, String documentId, User currentUser) {
        checkAccess(userId, currentUser);
        HealthDocument document = documentRepository
                .findByIdAndUserId(documentId, currentUser.getId())
                .orElseThrow(() -> new HealthNotFoundException("Document not found: " + documentId));

        Path storageRoot = Paths.get(storageProperties.documents()).toAbsolutePath().normalize();
        Path absolute = storageRoot.resolve(document.getStoragePath()).normalize();
        if (!absolute.startsWith(storageRoot)) {
            throw new HealthValidationException("Invalid file path");
        }
        if (!Files.isRegularFile(absolute)) {
            throw new HealthNotFoundException("Document file missing on disk: " + documentId);
        }
        return new HealthDocumentDownload(document, absolute);
    }

    @Transactional
    public void delete(String userId, String documentId, User currentUser) {
        checkAccess(userId, currentUser);
        HealthDocument document = Objects.requireNonNull(documentRepository
                .findByIdAndUserId(documentId, currentUser.getId())
                .orElseThrow(() -> new HealthNotFoundException("Document not found: " + documentId)));

        // 1. Remove DB row first — this is the source of truth.
        documentRepository.delete(document);

        // 2. Best-effort file + parent-directory cleanup. Errors are logged, not thrown.
        Path storageRoot = Paths.get(storageProperties.documents()).toAbsolutePath().normalize();
        Path absolute = storageRoot.resolve(document.getStoragePath()).normalize();
        if (!absolute.startsWith(storageRoot)) {
            log.warn("Skipping cleanup of document {}: path outside storage root", document.getId());
            return;
        }
        try {
            Files.deleteIfExists(absolute);
            Path parent = absolute.getParent();
            if (parent != null && parent.startsWith(storageRoot) && !parent.equals(storageRoot)
                    && Files.isDirectory(parent)) {
                try (var stream = Files.list(parent)) {
                    if (stream.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Cleanup failed for document {}: {}", document.getId(), e.toString());
        }
    }

    /**
     * Streams all document metadata (not the bytes) as CSV.
     * Header row: {@code name,category,contentType,sizeBytes,createdAt}.
     */
    @Transactional(readOnly = true)
    public void exportCsv(String userId, OutputStream outputStream, User currentUser) {
        checkAccess(userId, currentUser);
        List<HealthDocument> docs = documentRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
        Writer writer = new java.io.OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        PrintWriter out = new PrintWriter(writer);
        out.println("name,category,contentType,sizeBytes,createdAt");
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        for (HealthDocument doc : docs) {
            out.println(String.join(",",
                    HealthJournalService.csvField(doc.getName()),
                    HealthJournalService.csvField(doc.getCategory() == null ? "" : doc.getCategory().displayName()),
                    HealthJournalService.csvField(doc.getContentType() == null ? "" : doc.getContentType()),
                    HealthJournalService.csvField(Long.toString(doc.getSizeBytes())),
                    HealthJournalService.csvField(doc.getCreatedAt() == null ? "" : fmt.format(doc.getCreatedAt()))
            ));
        }
        out.flush();
    }

    private boolean isAllowed(String declaredType, String fileName) {
        String normalizedType = declaredType == null ? null : declaredType.toLowerCase(Locale.ROOT).trim();
        String ext = extensionOf(fileName);

        for (Map.Entry<String, Set<String>> entry : ALLOWED_TYPES.entrySet()) {
            boolean typeMatches = normalizedType != null && normalizedType.equals(entry.getKey());
            boolean extMatches = ext != null && entry.getValue().contains(ext);
            if (typeMatches || extMatches) {
                return true;
            }
        }
        return false;
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void checkAccess(String userId, User currentUser) {
        if (!currentUser.getUsername().equals(userId)) {
            throw new HealthAccessDeniedException(
                    "Access denied: you can only access your own health data");
        }
    }
}
