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

@Service
public class MyDriveService {

    private static final Logger log = LoggerFactory.getLogger(MyDriveService.class);

    private static final long KB = 1024L;
    private static final long MB = KB * 1024;
    private static final long GB = MB * 1024;

    private final ItemMetadataRepository itemMetadataRepository;
    private final DrivePathProperties drivePathProperties;
    private final DriveCacheManager driveCacheManager;

    public MyDriveService(ItemMetadataRepository itemMetadataRepository, DrivePathProperties drivePathProperties, DriveCacheManager driveCacheManager) {
        this.itemMetadataRepository = itemMetadataRepository;
        this.drivePathProperties = drivePathProperties;
        this.driveCacheManager = driveCacheManager;
    }

    // -----------------------------------------------------------------------
    // Inlined utilities: DrivePathResolver, ItemIdGenerator, FileSizeFormatter
    // -----------------------------------------------------------------------

    /**
     * Resolves a (driveKey, userId) pair to an absolute Path on the filesystem.
     * For myDrive, appends userId to the base path.
     */
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

    /**
     * Generates a deterministic item ID from a drive key and relative path.
     * Returns "root" for empty/root-representing paths.
     */
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

    /**
     * Formats a byte count as a human-readable string (e.g., "1.5 MB").
     */
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

    // -----------------------------------------------------------------------
    // Permission checking
    // -----------------------------------------------------------------------

    /**
     * Checks whether the current user has permission to access the specified drive.
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
    // Drive contents
    // -----------------------------------------------------------------------

    /**
     * Retrieves the full contents of a drive as a flat map of DriveItems.
     */
    public Map<String, DriveItem> getDriveContents(String userId, String driveKey, User currentUser) {
        DriveKey key = DriveKey.fromString(driveKey);
        checkPermission(key, userId, currentUser, false);

        Path drivePath = resolveDrivePath(key, userId);

        // Auto-provision myDrive per-user directory if it doesn't exist
        if (key == DriveKey.myDrive && !Files.exists(drivePath)) {
            try {
                Files.createDirectories(drivePath);
            } catch (IOException e) {
                throw new DriveUnavailableException("Failed to auto-provision My Drive for user " + userId);
            }
        }

        // Verify drive path is accessible
        if (!Files.exists(drivePath) || !Files.isDirectory(drivePath)) {
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

        Path normalizedRoot = drivePath.toAbsolutePath().normalize();

        Map<String, DriveItem> flatMap = new LinkedHashMap<>();
        Map<String, List<String>> childrenMap = new HashMap<>();

        try (Stream<Path> paths = Files.walk(normalizedRoot)) {
            paths.forEach(path -> {
                Path normalizedPath = path.toAbsolutePath().normalize();
                if (!normalizedPath.startsWith(normalizedRoot)) {
                    return;
                }

                Path relativePath = normalizedRoot.relativize(normalizedPath);
                String relativeStr = relativePath.toString().replace('\\', '/');

                String id = generateItemId(driveKey, relativeStr);
                String name = normalizedPath.equals(normalizedRoot)
                        ? normalizedRoot.getFileName().toString()
                        : normalizedPath.getFileName().toString();

                boolean isDirectory = Files.isDirectory(normalizedPath);
                String type = isDirectory ? "folder" : "file";
                String fileType = null;
                String size = null;

                if (!isDirectory) {
                    String fileName = name;
                    int dotIndex = fileName.lastIndexOf('.');
                    fileType = (dotIndex >= 0 && dotIndex < fileName.length() - 1)
                            ? fileName.substring(dotIndex + 1)
                            : "";
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
                    Path parentRelative = normalizedRoot.relativize(normalizedPath.getParent());
                    String parentRelStr = parentRelative.toString().replace('\\', '/');
                    parentId = generateItemId(driveKey, parentRelStr);
                }

                DriveItem item = new DriveItem(id, name, type, fileType, size, null,
                        isDirectory ? new ArrayList<>() : List.of(), parentId);
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
                        parent.size(), parent.color(), entry.getValue(), parent.parentId());
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
                        item.size(), colorEntry.getValue(), item.children(), item.parentId());
                flatMap.put(colorEntry.getKey(), updated);
            }
        }

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

    public DriveItem createFolder(String userId, String driveKey, CreateFolderRequest request, User currentUser) {
        DriveKey key = DriveKey.fromString(driveKey);
        checkPermission(key, userId, currentUser, true);

        Path drivePath = resolveDrivePath(key, userId);
        Path normalizedRoot = drivePath.toAbsolutePath().normalize();

        Path parentPath = resolveItemPath(normalizedRoot, driveKey, request.parentId());
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

        Path relativePath = normalizedRoot.relativize(newFolderPath.toAbsolutePath().normalize());
        String relativeStr = relativePath.toString().replace('\\', '/');
        String id = generateItemId(driveKey, relativeStr);

        driveCacheManager.invalidate(userId, driveKey);

        return new DriveItem(id, request.name(), "folder", null, null, null, List.of(), request.parentId());
    }

    public DriveItem uploadFile(String userId, String driveKey, MultipartFile file, String parentId, User currentUser) {
        DriveKey key = DriveKey.fromString(driveKey);
        checkPermission(key, userId, currentUser, true);

        Path drivePath = resolveDrivePath(key, userId);
        Path normalizedRoot = drivePath.toAbsolutePath().normalize();

        Path parentPath = resolveItemPath(normalizedRoot, driveKey, parentId);
        if (parentPath == null || !Files.exists(parentPath) || !Files.isDirectory(parentPath)) {
            throw new DriveItemNotFoundException("Parent folder not found: " + parentId);
        }

        String fileName = file.getOriginalFilename();
        Path targetPath = parentPath.resolve(fileName);
        if (Files.exists(targetPath)) {
            throw new DriveItemConflictException("An item with name '" + fileName + "' already exists in the parent folder");
        }

        try {
            Files.copy(file.getInputStream(), targetPath);
        } catch (IOException e) {
            throw new DriveUnavailableException("Failed to upload file: " + fileName);
        }

        String fileType = "";
        if (fileName != null) {
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
                fileType = fileName.substring(dotIndex + 1);
            }
        }

        String size;
        try {
            size = formatFileSize(Files.size(targetPath));
        } catch (IOException e) {
            size = "0 B";
        }

        Path relativePath = normalizedRoot.relativize(targetPath.toAbsolutePath().normalize());
        String relativeStr = relativePath.toString().replace('\\', '/');
        String id = generateItemId(driveKey, relativeStr);

        driveCacheManager.invalidate(userId, driveKey);

        return new DriveItem(id, fileName, "file", fileType, size, null, List.of(), parentId);
    }

    public Path downloadFile(String userId, String driveKey, String itemId, User currentUser) {
        DriveKey key = DriveKey.fromString(driveKey);
        checkPermission(key, userId, currentUser, false);

        Path drivePath = resolveDrivePath(key, userId);
        Path normalizedRoot = drivePath.toAbsolutePath().normalize();

        Path itemPath = resolveItemPath(normalizedRoot, driveKey, itemId);
        if (itemPath == null || !Files.exists(itemPath)) {
            throw new DriveItemNotFoundException("Item not found: " + itemId);
        }

        if (Files.isDirectory(itemPath)) {
            throw new DownloadFolderException("Cannot download a folder: " + itemId);
        }

        return itemPath;
    }

    public DriveItem updateItem(String userId, String driveKey, String itemId, UpdateItemRequest request, User currentUser) {
        DriveKey key = DriveKey.fromString(driveKey);
        checkPermission(key, userId, currentUser, true);

        Path drivePath = resolveDrivePath(key, userId);
        Path normalizedRoot = drivePath.toAbsolutePath().normalize();

        Path itemPath = resolveItemPath(normalizedRoot, driveKey, itemId);
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
                        if (normalized.startsWith(normalizedRoot)) {
                            Path relative = normalizedRoot.relativize(normalized);
                            String relStr = relative.toString().replace('\\', '/');
                            String oldId = generateItemId(driveKey, relStr);
                            Path relFromItem = pathBeforeRename.relativize(normalized);
                            oldIdToRelativePath.put(oldId, relFromItem.toString().replace('\\', '/'));
                        }
                    });
                } catch (IOException e) {
                    throw new DriveUnavailableException("Failed to read item descendants");
                }
            } else {
                Path relative = normalizedRoot.relativize(pathBeforeRename.toAbsolutePath().normalize());
                String relStr = relative.toString().replace('\\', '/');
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

                Path newDescendantPath;
                if (relFromItem.isEmpty()) {
                    newDescendantPath = newPath;
                } else {
                    newDescendantPath = newPath.resolve(relFromItem);
                }

                Path newRelative = normalizedRoot.relativize(newDescendantPath.toAbsolutePath().normalize());
                String newRelStr = newRelative.toString().replace('\\', '/');
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

        // Build and return the updated DriveItem
        String name = currentPath.getFileName().toString();
        boolean isDirectory = Files.isDirectory(currentPath);
        String type = isDirectory ? "folder" : "file";
        String fileType = null;
        String size = null;

        if (!isDirectory) {
            int dotIndex = name.lastIndexOf('.');
            fileType = (dotIndex >= 0 && dotIndex < name.length() - 1)
                    ? name.substring(dotIndex + 1)
                    : "";
            try {
                size = formatFileSize(Files.size(currentPath));
            } catch (IOException e) {
                size = "0 B";
            }
        }

        String color = null;
        Optional<ItemMetadata> meta = itemMetadataRepository.findById(Objects.requireNonNull(currentItemId));
        if (meta.isPresent()) {
            color = meta.get().getColor();
        }

        String parentId;
        if (currentPath.toAbsolutePath().normalize().equals(normalizedRoot)) {
            parentId = null;
        } else {
            Path parentRelative = normalizedRoot.relativize(currentPath.getParent().toAbsolutePath().normalize());
            String parentRelStr = parentRelative.toString().replace('\\', '/');
            parentId = generateItemId(driveKey, parentRelStr);
        }

        List<String> children = List.of();
        if (isDirectory) {
            try (Stream<Path> childStream = Files.list(currentPath)) {
                children = childStream.map(child -> {
                    Path childRel = normalizedRoot.relativize(child.toAbsolutePath().normalize());
                    String childRelStr = childRel.toString().replace('\\', '/');
                    return generateItemId(driveKey, childRelStr);
                }).collect(Collectors.toList());
            } catch (IOException e) {
                children = List.of();
            }
        }

        driveCacheManager.invalidate(userId, driveKey);

        return new DriveItem(currentItemId, name, type, fileType, size, color, children, parentId);
    }

    public void deleteItem(String userId, String driveKey, String itemId, User currentUser) {
        DriveKey key = DriveKey.fromString(driveKey);
        checkPermission(key, userId, currentUser, true);

        if ("root".equals(itemId)) {
            throw new DriveRootDeletionException("Cannot delete the drive root");
        }

        Path drivePath = resolveDrivePath(key, userId);
        Path normalizedRoot = drivePath.toAbsolutePath().normalize();

        Path itemPath = resolveItemPath(normalizedRoot, driveKey, itemId);
        if (itemPath == null || !Files.exists(itemPath)) {
            throw new DriveItemNotFoundException("Item not found: " + itemId);
        }

        List<String> idsToDelete = new ArrayList<>();

        if (Files.isDirectory(itemPath)) {
            try (Stream<Path> descendants = Files.walk(itemPath)) {
                descendants.forEach(p -> {
                    Path normalized = p.toAbsolutePath().normalize();
                    if (normalized.startsWith(normalizedRoot)) {
                        Path relative = normalizedRoot.relativize(normalized);
                        String relStr = relative.toString().replace('\\', '/');
                        idsToDelete.add(generateItemId(driveKey, relStr));
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
            Path relative = normalizedRoot.relativize(itemPath.toAbsolutePath().normalize());
            String relStr = relative.toString().replace('\\', '/');
            idsToDelete.add(generateItemId(driveKey, relStr));

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

    public DriveItem moveItem(String userId, String driveKey, String itemId,
                              MoveRequest request, User currentUser) {
        // 1. Parse key, check permissions (isWrite=true) — also rejects mediaCache writes
        DriveKey key = DriveKey.fromString(driveKey);
        checkPermission(key, userId, currentUser, true);

        // 2. Reject if itemId is "root"
        if ("root".equals(itemId)) {
            throw new DriveRootDeletionException("Cannot move the drive root");
        }

        // 3. Resolve source and destination paths
        Path drivePath = resolveDrivePath(key, userId);
        Path normalizedRoot = drivePath.toAbsolutePath().normalize();

        Path sourcePath = resolveItemPath(normalizedRoot, driveKey, itemId);
        if (sourcePath == null || !Files.exists(sourcePath)) {
            throw new DriveItemNotFoundException("Item not found: " + itemId);
        }

        Path destParentPath = resolveItemPath(normalizedRoot, driveKey, request.parentId());
        if (destParentPath == null || !Files.exists(destParentPath) || !Files.isDirectory(destParentPath)) {
            throw new DriveItemNotFoundException("Destination folder not found: " + request.parentId());
        }

        // 4. Check circular nesting: walk from destination parent up to root
        if (Files.isDirectory(sourcePath)) {
            Path normalizedSource = sourcePath.toAbsolutePath().normalize();
            Path current = destParentPath.toAbsolutePath().normalize();
            while (current != null && current.startsWith(normalizedRoot)) {
                if (current.equals(normalizedSource)) {
                    throw new DriveItemConflictException(
                            "Cannot move a folder into one of its own descendants");
                }
                current = current.getParent();
            }
        }

        // 5. Check name conflict in destination
        Path targetPath = destParentPath.resolve(sourcePath.getFileName());
        if (Files.exists(targetPath) && !targetPath.toAbsolutePath().normalize()
                .equals(sourcePath.toAbsolutePath().normalize())) {
            throw new DriveItemConflictException(
                    "An item with name '" + sourcePath.getFileName() + "' already exists in the destination folder");
        }

        // 6. Perform filesystem move
        try {
            Files.move(sourcePath, targetPath);
        } catch (IOException e) {
            throw new DriveUnavailableException("Failed to move item: " + itemId);
        }

        // 7. Invalidate cache
        driveCacheManager.invalidate(userId, driveKey);

        // 8. Build and return updated DriveItem
        Path newRelativePath = normalizedRoot.relativize(targetPath.toAbsolutePath().normalize());
        String newRelativeStr = newRelativePath.toString().replace('\\', '/');
        String newId = generateItemId(driveKey, newRelativeStr);
        String name = targetPath.getFileName().toString();
        boolean isDirectory = Files.isDirectory(targetPath);

        String type = isDirectory ? "folder" : "file";
        String fileType = null;
        String size = null;

        if (!isDirectory) {
            int dotIndex = name.lastIndexOf('.');
            fileType = (dotIndex >= 0 && dotIndex < name.length() - 1)
                    ? name.substring(dotIndex + 1)
                    : "";
            try {
                size = formatFileSize(Files.size(targetPath));
            } catch (IOException e) {
                size = "0 B";
            }
        }

        List<String> children = List.of();
        if (isDirectory) {
            try (Stream<Path> directChildren = Files.list(targetPath)) {
                children = directChildren.map(child -> {
                    Path childRelative = normalizedRoot.relativize(child.toAbsolutePath().normalize());
                    String childRelStr = childRelative.toString().replace('\\', '/');
                    return generateItemId(driveKey, childRelStr);
                }).collect(Collectors.toList());
            } catch (IOException e) {
                children = List.of();
            }
        }

        return new DriveItem(newId, name, type, fileType, size, null, children, request.parentId());
    }

    // -----------------------------------------------------------------------
    // Plex upload
    // -----------------------------------------------------------------------

    public PlexUploadResponse uploadToPlex(String userId, MultipartFile file, User currentUser) {
        String plexUploadPath = drivePathProperties.plexUpload();

        if (plexUploadPath == null || plexUploadPath.isBlank()) {
            throw new DriveUnavailableException("Plex upload path is not configured");
        }

        Path plexDir = Path.of(plexUploadPath).toAbsolutePath().normalize();

        if (!Files.exists(plexDir) || !Files.isDirectory(plexDir)) {
            throw new DriveUnavailableException("Plex upload path is not accessible: " + plexUploadPath);
        }

        String fileName = file.getOriginalFilename();

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
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Resolves an item ID to its filesystem path by walking the drive root.
     * For "root", returns the drive root itself.
     * Returns null if no matching path is found.
     */
    Path resolveItemPath(Path driveRoot, String driveKey, String itemId) {
        if ("root".equals(itemId)) {
            return driveRoot;
        }

        try (Stream<Path> paths = Files.walk(driveRoot)) {
            return paths
                    .filter(path -> {
                        Path normalizedPath = path.toAbsolutePath().normalize();
                        if (!normalizedPath.startsWith(driveRoot)) {
                            return false;
                        }
                        Path relative = driveRoot.relativize(normalizedPath);
                        String relativeStr = relative.toString().replace('\\', '/');
                        String generatedId = generateItemId(driveKey, relativeStr);
                        return generatedId.equals(itemId);
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
