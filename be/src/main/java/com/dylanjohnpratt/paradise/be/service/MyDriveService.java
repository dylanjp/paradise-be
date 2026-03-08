package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.dto.CreateFolderRequest;
import com.dylanjohnpratt.paradise.be.dto.DriveItem;
import com.dylanjohnpratt.paradise.be.dto.MoveRequest;
import com.dylanjohnpratt.paradise.be.dto.PlexUploadResponse;
import com.dylanjohnpratt.paradise.be.dto.UpdateItemRequest;
import com.dylanjohnpratt.paradise.be.exception.DownloadFolderException;
import com.dylanjohnpratt.paradise.be.exception.DriveAccessDeniedException;
import com.dylanjohnpratt.paradise.be.exception.DriveItemConflictException;
import com.dylanjohnpratt.paradise.be.exception.DriveItemNotFoundException;
import com.dylanjohnpratt.paradise.be.exception.DriveRootDeletionException;
import com.dylanjohnpratt.paradise.be.exception.DriveUnavailableException;
import com.dylanjohnpratt.paradise.be.exception.InvalidDriveKeyException;
import com.dylanjohnpratt.paradise.be.model.DriveKey;
import com.dylanjohnpratt.paradise.be.model.ItemMetadata;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for virtual drive file management operations.
 * Provides CRUD operations (browse, create, upload, download, rename, move, delete) for files
 * and folders across four drive types: myDrive (per-user), sharedDrive (all users),
 * adminDrive (admin-only), and mediaCache (read-only). Manages filesystem-to-ID mapping
 * using SHA-256 hashes of relative paths, with in-memory caching via {@link DriveCacheManager}
 * for O(1) item resolution. Also handles Plex media server uploads.
 */
@Service
public class MyDriveService {

    private static final Logger log = LoggerFactory.getLogger(MyDriveService.class);

    private static final long KB = 1024L;
    private static final long MB = KB * 1024;
    private static final long GB = MB * 1024;
    private static final int MAX_FILENAME_LENGTH = 255;

    private final ItemMetadataRepository itemMetadataRepository;
    private final DrivePathProperties drivePathProperties;
    private final DriveCacheManager driveCacheManager;

    public MyDriveService(ItemMetadataRepository itemMetadataRepository, DrivePathProperties drivePathProperties, DriveCacheManager driveCacheManager) {
        this.itemMetadataRepository = itemMetadataRepository;
        this.drivePathProperties = drivePathProperties;
        this.driveCacheManager = driveCacheManager;
    }

    // -----------------------------------------------------------------------
    // DriveContext — resolves and validates drive key, permissions, and root path
    // -----------------------------------------------------------------------

    record DriveContext(DriveKey key, Path normalizedRoot) {}

    private DriveContext resolveAndCheck(String driveKey, String userId, User currentUser, boolean isWrite) {
        DriveKey key = DriveKey.fromString(driveKey);
        checkPermission(key, userId, currentUser, isWrite);
        Path drivePath = resolveDrivePath(key, userId);
        Path normalizedRoot = drivePath.toAbsolutePath().normalize();
        return new DriveContext(key, normalizedRoot);
    }

    // -----------------------------------------------------------------------
    // Inlined utilities
    // -----------------------------------------------------------------------

    Path resolveDrivePath(DriveKey driveKey, String userId) {
        String basePath = switch (driveKey) {
            case myDrive -> drivePathProperties.myDrive();
            case sharedDrive -> drivePathProperties.sharedDrive();
            case adminDrive -> drivePathProperties.adminDrive();
            case mediaCache -> drivePathProperties.mediaCache();
        };
        if (driveKey == DriveKey.myDrive) {
            return Path.of(basePath).resolve(userId);
        }
        return Path.of(basePath);
    }

    static String generateItemId(String driveKey, String relativePath) {
        if (relativePath == null || relativePath.isEmpty()
                || relativePath.equals("/") || relativePath.equals(".")) {
            return "root";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (driveKey + "/" + relativePath).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    static String formatFileSize(long bytes) {
        if (bytes < KB) {
            return bytes + " B";
        } else if (bytes < MB) {
            return formatSizeUnit(bytes, KB, "KB");
        } else if (bytes < GB) {
            return formatSizeUnit(bytes, MB, "MB");
        } else {
            return formatSizeUnit(bytes, GB, "GB");
        }
    }

    private static String formatSizeUnit(long bytes, long unit, String suffix) {
        double value = (double) bytes / unit;
        if (value == Math.floor(value)) {
            return (long) value + " " + suffix;
        }
        return String.format("%.1f %s", value, suffix);
    }

    /**
     * Sanitizes a filename from a multipart upload.
     * Strips path separators, rejects null/blank, and limits length.
     */
    static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidDriveKeyException("File name is required");
        }
        String sanitized = fileName.replace("/", "").replace("\\", "");
        if (sanitized.isBlank()) {
            throw new InvalidDriveKeyException("File name is invalid");
        }
        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            sanitized = sanitized.substring(sanitized.length() - MAX_FILENAME_LENGTH);
        }
        return sanitized;
    }

    private static String extractFileType(String name) {
        if (name == null) return "";
        int dotIndex = name.lastIndexOf('.');
        return (dotIndex >= 0 && dotIndex < name.length() - 1)
                ? name.substring(dotIndex + 1)
                : "";
    }

    private static String toRelativeStr(Path normalizedRoot, Path path) {
        return normalizedRoot.relativize(path.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    // -----------------------------------------------------------------------
    // Permission checking
    // -----------------------------------------------------------------------

    /**
     * Validates that the current user has the required access level for the specified drive.
     * myDrive requires ownership, adminDrive requires ROLE_ADMIN, mediaCache is read-only,
     * and sharedDrive is open to all authenticated users.
     *
     * @param driveKey    the drive type being accessed
     * @param userId      the drive owner's user ID
     * @param currentUser the authenticated user requesting access
     * @param isWrite     true if the operation modifies data (create, upload, update, move, delete)
     * @throws DriveAccessDeniedException if the user lacks the required permissions
     * @throws InvalidDriveKeyException   if the drive key is null
     */
    public void checkPermission(DriveKey driveKey, String userId, User currentUser, boolean isWrite) {
        if (driveKey == null) {
            throw new InvalidDriveKeyException("Invalid drive key");
        }

        switch (driveKey) {
            case myDrive -> {
                if (!currentUser.getUsername().equals(userId)) {
                    throw new DriveAccessDeniedException("Access denied: you can only access your own drive");
                }
            }
            case sharedDrive -> { }
            case adminDrive -> {
                boolean isAdmin = currentUser.getAuthorities().stream()
                        .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
                if (!isAdmin) {
                    throw new DriveAccessDeniedException("Access denied: admin drive requires ROLE_ADMIN");
                }
            }
            case mediaCache -> {
                if (isWrite) {
                    throw new DriveAccessDeniedException("Access denied: media cache is read-only");
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Build DriveItem from filesystem path (shared helper)
    // -----------------------------------------------------------------------

    private DriveItem buildDriveItemFromPath(Path path, Path normalizedRoot, String driveKey, String color) {
        String relativeStr = toRelativeStr(normalizedRoot, path);
        String id = generateItemId(driveKey, relativeStr);
        String name = path.getFileName().toString();
        boolean isDirectory = Files.isDirectory(path);
        String type = isDirectory ? "folder" : "file";
        String fileType = null;
        String size = null;

        if (!isDirectory) {
            fileType = extractFileType(name);
            try {
                size = formatFileSize(Files.size(path));
            } catch (IOException e) {
                size = "0 B";
            }
        }

        String parentId;
        if (path.toAbsolutePath().normalize().equals(normalizedRoot)) {
            parentId = null;
        } else {
            parentId = generateItemId(driveKey, toRelativeStr(normalizedRoot, path.getParent()));
        }

        List<String> children = List.of();
        if (isDirectory) {
            try (Stream<Path> childStream = Files.list(path)) {
                children = childStream.map(child -> generateItemId(driveKey, toRelativeStr(normalizedRoot, child)))
                        .collect(Collectors.toList());
            } catch (IOException e) {
                children = List.of();
            }
        }

        return new DriveItem(id, name, type, fileType, size, color, children, parentId, relativeStr);
    }

    // -----------------------------------------------------------------------
    // Item path resolution (O(1) via cache, fallback to walk)
    // -----------------------------------------------------------------------

    Path resolveItemPath(Path driveRoot, String driveKey, String itemId, String userId) {
        if ("root".equals(itemId)) {
            return driveRoot;
        }

        // Try O(1) lookup from cache
        try {
            Optional<Map<String, DriveItem>> cached = driveCacheManager.get(userId, driveKey);
            if (cached.isPresent()) {
                DriveItem item = cached.get().get(itemId);
                if (item != null && item.relativePath() != null) {
                    Path resolved = driveRoot.resolve(item.relativePath()).toAbsolutePath().normalize();
                    if (Files.exists(resolved) && resolved.startsWith(driveRoot)) {
                        return resolved;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Cache lookup failed for item resolution, falling back to walk", e);
        }

        // Fallback: walk filesystem
        try (Stream<Path> paths = Files.walk(driveRoot)) {
            return paths
                    .filter(path -> {
                        Path normalizedPath = path.toAbsolutePath().normalize();
                        if (!normalizedPath.startsWith(driveRoot)) {
                            return false;
                        }
                        String relativeStr = toRelativeStr(driveRoot, normalizedPath);
                        return generateItemId(driveKey, relativeStr).equals(itemId);
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Drive contents
    // -----------------------------------------------------------------------

    /**
     * Retrieves the full contents of a drive as a flat map of item IDs to {@link DriveItem} records.
     * Checks the in-memory cache first; if stale or absent, performs a full filesystem walk and
     * merges item metadata (colors) from the database. Auto-provisions the myDrive directory
     * for new users if it does not exist.
     *
     * @param userId      the drive owner's user ID
     * @param driveKey    the drive type identifier string
     * @param currentUser the authenticated user requesting access
     * @return a map of SHA-256 item IDs to their {@link DriveItem} metadata
     * @throws DriveUnavailableException if the drive path is inaccessible
     */
    public Map<String, DriveItem> getDriveContents(String userId, String driveKey, User currentUser) {
        DriveContext ctx = resolveAndCheck(driveKey, userId, currentUser, false);

        // Auto-provision myDrive per-user directory if it doesn't exist
        if (ctx.key() == DriveKey.myDrive && !Files.exists(ctx.normalizedRoot())) {
            try {
                Files.createDirectories(ctx.normalizedRoot());
            } catch (IOException e) {
                throw new DriveUnavailableException("Failed to auto-provision My Drive for user " + userId);
            }
        }

        if (!Files.exists(ctx.normalizedRoot()) || !Files.isDirectory(ctx.normalizedRoot())) {
            throw new DriveUnavailableException("Drive path is not accessible: " + driveKey);
        }

        // Cache lookup
        try {
            if (driveCacheManager.isEnabled(driveKey)) {
                Optional<Map<String, DriveItem>> cached = driveCacheManager.get(userId, driveKey);
                if (cached.isPresent()) {
                    return cached.get();
                }
            }
        } catch (Exception e) {
            log.error("Cache lookup failed for userId={}, driveKey={}, falling back to traversal", userId, driveKey, e);
        }

        Map<String, DriveItem> flatMap = buildFlatMap(ctx.normalizedRoot(), driveKey);

        // Cache store
        try {
            if (driveCacheManager.isEnabled(driveKey)) {
                driveCacheManager.put(userId, driveKey, flatMap);
            }
        } catch (Exception e) {
            log.error("Cache store failed for userId={}, driveKey={}", userId, driveKey, e);
        }

        return flatMap;
    }

    // -----------------------------------------------------------------------
    // CRUD operations
    // -----------------------------------------------------------------------

    /**
     * Creates a new folder inside the specified parent folder within the drive.
     * Invalidates the drive cache after the operation.
     *
     * @param userId      the drive owner's user ID
     * @param driveKey    the drive type identifier string
     * @param request     the folder creation request containing the name and parent ID
     * @param currentUser the authenticated user requesting the operation
     * @return the newly created folder as a {@link DriveItem}
     * @throws DriveItemNotFoundException if the parent folder does not exist
     * @throws DriveItemConflictException if a folder with the same name already exists
     */
    public DriveItem createFolder(String userId, String driveKey, CreateFolderRequest request, User currentUser) {
        DriveContext ctx = resolveAndCheck(driveKey, userId, currentUser, true);

        Path parentPath = resolveItemPath(ctx.normalizedRoot(), driveKey, request.parentId(), userId);
        if (parentPath == null || !Files.exists(parentPath) || !Files.isDirectory(parentPath)) {
            throw new DriveItemNotFoundException("Parent folder not found: " + request.parentId());
        }

        Path newFolderPath = parentPath.resolve(request.name());
        if (Files.exists(newFolderPath)) {
            throw new DriveItemConflictException("An item with name '" + request.name() + "' already exists in the parent folder");
        }

        try {
            Files.createDirectory(newFolderPath);
        } catch (IOException e) {
            throw new DriveUnavailableException("Failed to create folder: " + request.name());
        }

        String relativeStr = toRelativeStr(ctx.normalizedRoot(), newFolderPath);
        String id = generateItemId(driveKey, relativeStr);

        driveCacheManager.invalidate(userId, driveKey);

        return new DriveItem(id, request.name(), "folder", null, null, null, List.of(), request.parentId(), relativeStr);
    }

    /**
     * Uploads a file to the specified parent folder within the drive.
     * The filename is sanitized to strip path separators and enforce a 255-character limit.
     * Invalidates the drive cache after the operation.
     *
     * @param userId      the drive owner's user ID
     * @param driveKey    the drive type identifier string
     * @param file        the multipart file to upload
     * @param parentId    the item ID of the parent folder
     * @param currentUser the authenticated user requesting the operation
     * @return the uploaded file as a {@link DriveItem}
     * @throws DriveItemNotFoundException if the parent folder does not exist
     * @throws DriveItemConflictException if a file with the same name already exists
     */
    public DriveItem uploadFile(String userId, String driveKey, MultipartFile file, String parentId, User currentUser) {
        DriveContext ctx = resolveAndCheck(driveKey, userId, currentUser, true);

        Path parentPath = resolveItemPath(ctx.normalizedRoot(), driveKey, parentId, userId);
        if (parentPath == null || !Files.exists(parentPath) || !Files.isDirectory(parentPath)) {
            throw new DriveItemNotFoundException("Parent folder not found: " + parentId);
        }

        String fileName = sanitizeFileName(file.getOriginalFilename());
        Path targetPath = parentPath.resolve(fileName);
        if (Files.exists(targetPath)) {
            throw new DriveItemConflictException("An item with name '" + fileName + "' already exists in the parent folder");
        }

        try {
            Files.copy(file.getInputStream(), targetPath);
        } catch (IOException e) {
            throw new DriveUnavailableException("Failed to upload file: " + fileName);
        }

        String fileType = extractFileType(fileName);

        String size;
        try {
            size = formatFileSize(Files.size(targetPath));
        } catch (IOException e) {
            size = "0 B";
        }

        String relativeStr = toRelativeStr(ctx.normalizedRoot(), targetPath);
        String id = generateItemId(driveKey, relativeStr);

        driveCacheManager.invalidate(userId, driveKey);

        return new DriveItem(id, fileName, "file", fileType, size, null, List.of(), parentId, relativeStr);
    }

    /**
     * Resolves and returns the filesystem path for a file to be downloaded.
     * The caller (controller) is responsible for streaming the file contents to the response.
     *
     * @param userId      the drive owner's user ID
     * @param driveKey    the drive type identifier string
     * @param itemId      the SHA-256 hash ID of the file to download
     * @param currentUser the authenticated user requesting the operation
     * @return the resolved filesystem {@link Path} to the file
     * @throws DriveItemNotFoundException if the item does not exist
     * @throws DownloadFolderException    if the item is a folder (folders cannot be downloaded)
     */
    public Path downloadFile(String userId, String driveKey, String itemId, User currentUser) {
        DriveContext ctx = resolveAndCheck(driveKey, userId, currentUser, false);

        Path itemPath = resolveItemPath(ctx.normalizedRoot(), driveKey, itemId, userId);
        if (itemPath == null || !Files.exists(itemPath)) {
            throw new DriveItemNotFoundException("Item not found: " + itemId);
        }

        if (Files.isDirectory(itemPath)) {
            throw new DownloadFolderException("Cannot download a folder: " + itemId);
        }

        return itemPath;
    }

    /**
     * Updates properties of a drive item (file or folder). Supports renaming and color changes.
     * When renaming a folder, regenerates SHA-256 IDs for the item and all descendants, and
     * migrates any associated metadata records to the new IDs. Invalidates the drive cache.
     *
     * @param userId      the drive owner's user ID
     * @param driveKey    the drive type identifier string
     * @param itemId      the SHA-256 hash ID of the item to update
     * @param request     the update request containing optional new name and/or color
     * @param currentUser the authenticated user requesting the operation
     * @return the updated item as a {@link DriveItem} (with new ID if renamed)
     * @throws DriveItemNotFoundException if the item does not exist
     * @throws DriveItemConflictException if renaming would create a name collision
     */
    public DriveItem updateItem(String userId, String driveKey, String itemId, UpdateItemRequest request, User currentUser) {
        DriveContext ctx = resolveAndCheck(driveKey, userId, currentUser, true);

        Path itemPath = resolveItemPath(ctx.normalizedRoot(), driveKey, itemId, userId);
        if (itemPath == null || !Files.exists(itemPath)) {
            throw new DriveItemNotFoundException("Item not found: " + itemId);
        }

        Path currentPath = itemPath;
        String currentItemId = itemId;

        // Handle rename
        if (request.name() != null) {
            Path parentDir = currentPath.getParent();
            Path newPath = parentDir.resolve(request.name());

            if (Files.exists(newPath)) {
                throw new DriveItemConflictException("An item with name '" + request.name() + "' already exists in the parent folder");
            }

            Map<String, String> oldIdToRelativePath = new LinkedHashMap<>();
            final Path pathBeforeRename = currentPath;
            if (Files.isDirectory(pathBeforeRename)) {
                try (Stream<Path> descendants = Files.walk(pathBeforeRename)) {
                    descendants.forEach(p -> {
                        Path normalized = p.toAbsolutePath().normalize();
                        if (normalized.startsWith(ctx.normalizedRoot())) {
                            String relStr = toRelativeStr(ctx.normalizedRoot(), normalized);
                            String oldId = generateItemId(driveKey, relStr);
                            Path relFromItem = pathBeforeRename.relativize(normalized);
                            oldIdToRelativePath.put(oldId, relFromItem.toString().replace('\\', '/'));
                        }
                    });
                } catch (IOException e) {
                    throw new DriveUnavailableException("Failed to read item descendants");
                }
            } else {
                String relStr = toRelativeStr(ctx.normalizedRoot(), pathBeforeRename);
                oldIdToRelativePath.put(generateItemId(driveKey, relStr), "");
            }

            try {
                Files.move(pathBeforeRename, newPath);
            } catch (IOException e) {
                throw new DriveUnavailableException("Failed to rename item: " + request.name());
            }

            Map<String, String> oldToNewIdMap = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : oldIdToRelativePath.entrySet()) {
                String oldId = entry.getKey();
                String relFromItem = entry.getValue();

                Path newDescendantPath = relFromItem.isEmpty() ? newPath : newPath.resolve(relFromItem);
                String newRelStr = toRelativeStr(ctx.normalizedRoot(), newDescendantPath);
                String newId = generateItemId(driveKey, newRelStr);
                oldToNewIdMap.put(oldId, newId);
            }

            List<String> oldIds = new ArrayList<>(oldToNewIdMap.keySet());
            List<ItemMetadata> existingMetadata = itemMetadataRepository.findAllById(oldIds);

            if (!existingMetadata.isEmpty()) {
                itemMetadataRepository.deleteByItemIdIn(
                        existingMetadata.stream().map(ItemMetadata::getItemId).collect(Collectors.toList()));
                itemMetadataRepository.flush();

                List<ItemMetadata> newMetadata = existingMetadata.stream()
                        .map(m -> new ItemMetadata(
                                Objects.requireNonNull(oldToNewIdMap.get(m.getItemId())),
                                m.getDriveKey(), m.getColor()))
                        .collect(Collectors.toList());
                @SuppressWarnings({ "null", "unused" })
                var saved = itemMetadataRepository.saveAll(newMetadata);
            }

            currentPath = newPath;
            currentItemId = oldToNewIdMap.getOrDefault(itemId, itemId);
        }

        // Handle color upsert
        if (request.color() != null) {
            Optional<ItemMetadata> existing = itemMetadataRepository.findById(Objects.requireNonNull(currentItemId));
            if (existing.isPresent()) {
                ItemMetadata meta2 = existing.get();
                meta2.setColor(request.color());
                itemMetadataRepository.save(meta2);
            } else {
                ItemMetadata metadata = new ItemMetadata(currentItemId, driveKey, request.color());
                itemMetadataRepository.save(metadata);
            }
        }

        String color = itemMetadataRepository.findById(Objects.requireNonNull(currentItemId))
                .map(ItemMetadata::getColor)
                .orElse(null);

        driveCacheManager.invalidate(userId, driveKey);

        return buildDriveItemFromPath(currentPath, ctx.normalizedRoot(), driveKey, color);
    }

    /**
     * Deletes a file or folder from the drive. For folders, recursively deletes all contents.
     * Also removes any associated metadata records from the database. The drive root
     * cannot be deleted. Invalidates the drive cache after the operation.
     *
     * @param userId      the drive owner's user ID
     * @param driveKey    the drive type identifier string
     * @param itemId      the SHA-256 hash ID of the item to delete
     * @param currentUser the authenticated user requesting the operation
     * @throws DriveRootDeletionException if attempting to delete the drive root
     * @throws DriveItemNotFoundException if the item does not exist
     */
    public void deleteItem(String userId, String driveKey, String itemId, User currentUser) {
        DriveContext ctx = resolveAndCheck(driveKey, userId, currentUser, true);

        if ("root".equals(itemId)) {
            throw new DriveRootDeletionException("Cannot delete the drive root");
        }

        Path itemPath = resolveItemPath(ctx.normalizedRoot(), driveKey, itemId, userId);
        if (itemPath == null || !Files.exists(itemPath)) {
            throw new DriveItemNotFoundException("Item not found: " + itemId);
        }

        List<String> idsToDelete = new ArrayList<>();

        if (Files.isDirectory(itemPath)) {
            try (Stream<Path> descendants = Files.walk(itemPath)) {
                descendants.forEach(p -> {
                    Path normalized = p.toAbsolutePath().normalize();
                    if (normalized.startsWith(ctx.normalizedRoot())) {
                        idsToDelete.add(generateItemId(driveKey, toRelativeStr(ctx.normalizedRoot(), normalized)));
                    }
                });
            } catch (IOException e) {
                throw new DriveUnavailableException("Failed to read item descendants for deletion");
            }

            try {
                Files.walkFileTree(itemPath, new java.nio.file.SimpleFileVisitor<Path>() {
                    @Override
                    public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    @Override
                    public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new DriveUnavailableException("Failed to delete directory: " + itemId);
            }
        } else {
            idsToDelete.add(generateItemId(driveKey, toRelativeStr(ctx.normalizedRoot(), itemPath)));

            try {
                Files.delete(itemPath);
            } catch (IOException e) {
                throw new DriveUnavailableException("Failed to delete file: " + itemId);
            }
        }

        if (!idsToDelete.isEmpty()) {
            itemMetadataRepository.deleteByItemIdIn(idsToDelete);
        }

        driveCacheManager.invalidate(userId, driveKey);
    }

    // -----------------------------------------------------------------------
    // Move item
    // -----------------------------------------------------------------------

    /**
     * Moves a file or folder to a different parent folder within the same drive.
     * Validates against circular nesting (moving a folder into its own descendant)
     * and name conflicts in the destination. Invalidates the drive cache after the operation.
     *
     * @param userId      the drive owner's user ID
     * @param driveKey    the drive type identifier string
     * @param itemId      the SHA-256 hash ID of the item to move
     * @param request     the move request containing the destination parent folder ID
     * @param currentUser the authenticated user requesting the operation
     * @return the moved item as a {@link DriveItem} at its new location
     * @throws DriveRootDeletionException if attempting to move the drive root
     * @throws DriveItemNotFoundException if the item or destination does not exist
     * @throws DriveItemConflictException if circular nesting or name conflict detected
     */
    public DriveItem moveItem(String userId, String driveKey, String itemId,
                              MoveRequest request, User currentUser) {
        DriveContext ctx = resolveAndCheck(driveKey, userId, currentUser, true);

        if ("root".equals(itemId)) {
            throw new DriveRootDeletionException("Cannot move the drive root");
        }

        Path sourcePath = resolveItemPath(ctx.normalizedRoot(), driveKey, itemId, userId);
        if (sourcePath == null || !Files.exists(sourcePath)) {
            throw new DriveItemNotFoundException("Item not found: " + itemId);
        }

        Path destParentPath = resolveItemPath(ctx.normalizedRoot(), driveKey, request.parentId(), userId);
        if (destParentPath == null || !Files.exists(destParentPath) || !Files.isDirectory(destParentPath)) {
            throw new DriveItemNotFoundException("Destination folder not found: " + request.parentId());
        }

        // Check circular nesting
        if (Files.isDirectory(sourcePath)) {
            Path normalizedSource = sourcePath.toAbsolutePath().normalize();
            Path current = destParentPath.toAbsolutePath().normalize();
            while (current != null && current.startsWith(ctx.normalizedRoot())) {
                if (current.equals(normalizedSource)) {
                    throw new DriveItemConflictException(
                            "Cannot move a folder into one of its own descendants");
                }
                current = current.getParent();
            }
        }

        // Check name conflict
        Path targetPath = destParentPath.resolve(sourcePath.getFileName());
        if (Files.exists(targetPath) && !targetPath.toAbsolutePath().normalize()
                .equals(sourcePath.toAbsolutePath().normalize())) {
            throw new DriveItemConflictException(
                    "An item with name '" + sourcePath.getFileName() + "' already exists in the destination folder");
        }

        try {
            Files.move(sourcePath, targetPath);
        } catch (IOException e) {
            throw new DriveUnavailableException("Failed to move item: " + itemId);
        }

        driveCacheManager.invalidate(userId, driveKey);

        return buildDriveItemFromPath(targetPath, ctx.normalizedRoot(), driveKey, null);
    }

    // -----------------------------------------------------------------------
    // Plex upload
    // -----------------------------------------------------------------------

    /**
     * Uploads a file to the Plex media server staging directory.
     * The file is saved to the configured plexUpload path on the server filesystem.
     * The filename is sanitized before saving. Rejects uploads if a file with the
     * same name already exists.
     *
     * @param userId      the user ID performing the upload
     * @param file        the multipart file to upload
     * @param currentUser the authenticated user requesting the operation
     * @return a {@link PlexUploadResponse} with the filename and formatted size
     * @throws DriveUnavailableException  if the Plex upload path is not configured or accessible
     * @throws DriveItemConflictException if a file with the same name already exists
     */
    public PlexUploadResponse uploadToPlex(String userId, MultipartFile file, User currentUser) {
        String plexUploadPath = drivePathProperties.plexUpload();

        if (plexUploadPath == null || plexUploadPath.isBlank()) {
            throw new DriveUnavailableException("Plex upload path is not configured");
        }

        Path plexDir = Path.of(plexUploadPath).toAbsolutePath().normalize();

        if (!Files.exists(plexDir) || !Files.isDirectory(plexDir)) {
            throw new DriveUnavailableException("Plex upload path is not accessible: " + plexUploadPath);
        }

        String fileName = sanitizeFileName(file.getOriginalFilename());

        Path targetPath = plexDir.resolve(fileName);
        if (Files.exists(targetPath)) {
            throw new DriveItemConflictException("A file with name '" + fileName + "' already exists in the Plex upload folder");
        }

        try {
            Files.copy(file.getInputStream(), targetPath);
        } catch (IOException e) {
            throw new DriveUnavailableException("Failed to upload file to Plex: " + fileName);
        }

        String size;
        try {
            size = formatFileSize(Files.size(targetPath));
        } catch (IOException e) {
            size = "0 B";
        }

        return new PlexUploadResponse(fileName, size);
    }

    // -----------------------------------------------------------------------
    // Cache warming
    // -----------------------------------------------------------------------

    /**
     * Pre-populates the in-memory cache for a global drive (sharedDrive or mediaCache).
     * Performs a full filesystem walk and stores the result in the global cache slot.
     * Used by the MediaCacheWarmupScheduler on startup and periodically to prevent
     * cold cache hits for shared drives.
     *
     * @param driveKey the drive type identifier string (should be a global drive)
     */
    public void warmDriveCache(String driveKey) {
        DriveKey key = DriveKey.fromString(driveKey);
        if (key == null) {
            log.warn("Cannot warm cache for invalid drive key: {}", driveKey);
            return;
        }

        Path drivePath = resolveDrivePath(key, "system");
        if (!Files.exists(drivePath) || !Files.isDirectory(drivePath)) {
            log.warn("Drive path not accessible for cache warming: {}", driveKey);
            return;
        }

        Path normalizedRoot = drivePath.toAbsolutePath().normalize();
        Map<String, DriveItem> flatMap = buildFlatMap(normalizedRoot, driveKey);
        driveCacheManager.putGlobal(driveKey, flatMap);
        log.info("Cache warmed for global drive: {} ({} items)", driveKey, flatMap.size());
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private Map<String, DriveItem> buildFlatMap(Path normalizedRoot, String driveKey) {
        Map<String, DriveItem> flatMap = new LinkedHashMap<>();
        Map<String, List<String>> childrenMap = new HashMap<>();

        try (Stream<Path> paths = Files.walk(normalizedRoot)) {
            paths.forEach(path -> {
                Path normalizedPath = path.toAbsolutePath().normalize();
                if (!normalizedPath.startsWith(normalizedRoot)) {
                    return;
                }

                String relativeStr = toRelativeStr(normalizedRoot, normalizedPath);

                String id = generateItemId(driveKey, relativeStr);
                String name = normalizedPath.equals(normalizedRoot)
                        ? normalizedRoot.getFileName().toString()
                        : normalizedPath.getFileName().toString();

                boolean isDirectory = Files.isDirectory(normalizedPath);
                String type = isDirectory ? "folder" : "file";
                String fileType = null;
                String size = null;

                if (!isDirectory) {
                    fileType = extractFileType(name);
                    try {
                        size = formatFileSize(Files.size(normalizedPath));
                    } catch (IOException e) {
                        size = "0 B";
                    }
                }

                String parentId;
                if (normalizedPath.equals(normalizedRoot)) {
                    parentId = null;
                } else {
                    parentId = generateItemId(driveKey, toRelativeStr(normalizedRoot, normalizedPath.getParent()));
                }

                DriveItem item = new DriveItem(id, name, type, fileType, size, null,
                        isDirectory ? new ArrayList<>() : List.of(), parentId, relativeStr);
                flatMap.put(id, item);

                if (parentId != null) {
                    childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(id);
                }
            });
        } catch (IOException e) {
            throw new DriveUnavailableException("Failed to read drive contents: " + driveKey);
        }

        // Populate children arrays for folders
        for (Map.Entry<String, List<String>> entry : childrenMap.entrySet()) {
            DriveItem parent = flatMap.get(entry.getKey());
            if (parent != null && "folder".equals(parent.type())) {
                DriveItem updated = new DriveItem(
                        parent.id(), parent.name(), parent.type(), parent.fileType(),
                        parent.size(), parent.color(), entry.getValue(), parent.parentId(),
                        parent.relativePath());
                flatMap.put(entry.getKey(), updated);
            }
        }

        // Merge metadata colors from database
        List<ItemMetadata> metadataList = itemMetadataRepository.findByDriveKey(driveKey);
        Map<String, String> colorMap = metadataList.stream()
                .collect(Collectors.toMap(ItemMetadata::getItemId, ItemMetadata::getColor));

        for (Map.Entry<String, String> colorEntry : colorMap.entrySet()) {
            DriveItem item = flatMap.get(colorEntry.getKey());
            if (item != null) {
                DriveItem updated = new DriveItem(
                        item.id(), item.name(), item.type(), item.fileType(),
                        item.size(), colorEntry.getValue(), item.children(), item.parentId(),
                        item.relativePath());
                flatMap.put(colorEntry.getKey(), updated);
            }
        }

        return flatMap;
    }
}
