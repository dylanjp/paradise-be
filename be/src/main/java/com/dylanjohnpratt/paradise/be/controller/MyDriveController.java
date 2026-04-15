package com.dylanjohnpratt.paradise.be.controller;

import com.dylanjohnpratt.paradise.be.dto.CreateFolderRequest;
import com.dylanjohnpratt.paradise.be.dto.DriveItem;
import com.dylanjohnpratt.paradise.be.dto.MoveRequest;
import com.dylanjohnpratt.paradise.be.dto.UpdateItemRequest;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.service.MyDriveService;
import jakarta.validation.Valid;
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
import java.nio.file.Path;
import java.util.Map;

/**
 * Controller for virtual drive file management operations.
 * Exposes RESTful endpoints for browsing, creating, uploading, downloading,
 * renaming, moving, and deleting files and folders within the four drive types
 * (myDrive, sharedDrive, adminDrive, mediaCache). Each drive type has its own
 * permission model enforced by {@link MyDriveService}.
 */
@RestController
@RequestMapping("/users/{userId}/drives/{driveKey}")
public class MyDriveController {

    private final MyDriveService myDriveService;

    public MyDriveController(MyDriveService myDriveService) {
        this.myDriveService = myDriveService;
    }

    /**
     * Retrieves the full contents of a drive as a flat map of item IDs to {@link DriveItem} records.
     * Results are cached in memory with a configurable TTL for frequently accessed drives.
     * Auto-provisions the myDrive directory for the user if it does not yet exist.
     *
     * @param userId      the owner of the drive (or any user for shared/media drives)
     * @param driveKey    the drive type identifier (myDrive, sharedDrive, adminDrive, mediaCache)
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return a map of item IDs to their {@link DriveItem} metadata
     */
    @GetMapping
    public ResponseEntity<Map<String, DriveItem>> getDriveContents(
            @PathVariable String userId,
            @PathVariable String driveKey,
            @AuthenticationPrincipal User currentUser) {
        Map<String, DriveItem> contents = myDriveService.getDriveContents(userId, driveKey, currentUser);
        return ResponseEntity.ok(contents);
    }

    /**
     * Creates a new folder inside the specified parent folder within the drive.
     * Returns 409 Conflict if a folder with the same name already exists in the parent.
     *
     * @param userId      the owner of the drive
     * @param driveKey    the drive type identifier
     * @param request     the folder creation request containing the folder name and parent ID
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the newly created folder as a {@link DriveItem} with HTTP 201 Created
     */
    @PostMapping("/folders")
    public ResponseEntity<DriveItem> createFolder(
            @PathVariable String userId,
            @PathVariable String driveKey,
            @Valid @RequestBody CreateFolderRequest request,
            @AuthenticationPrincipal User currentUser) {
        DriveItem folder = myDriveService.createFolder(userId, driveKey, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(folder);
    }

    /**
     * Uploads a file to the specified parent folder within the drive.
     * The filename is sanitized to strip path separators and enforce length limits.
     * Returns 409 Conflict if a file with the same name already exists in the parent.
     *
     * @param userId      the owner of the drive
     * @param driveKey    the drive type identifier
     * @param file        the multipart file to upload
     * @param parentId    the item ID of the parent folder to upload into
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the uploaded file as a {@link DriveItem} with HTTP 201 Created
     */
    @PostMapping("/files")
    public ResponseEntity<DriveItem> uploadFile(
            @PathVariable String userId,
            @PathVariable String driveKey,
            @RequestParam("file") MultipartFile file,
            @RequestParam String parentId,
            @AuthenticationPrincipal User currentUser) {
        DriveItem item = myDriveService.uploadFile(userId, driveKey, file, parentId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    /**
     * Downloads a file from the drive as a streaming response.
     * Probes the file's MIME type and sets Content-Disposition to attachment with the original filename.
     * Returns 400 Bad Request if the item is a folder (folders cannot be downloaded).
     *
     * @param userId      the owner of the drive
     * @param driveKey    the drive type identifier
     * @param itemId      the SHA-256 hash ID of the file to download
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return a streaming response body with the file contents and appropriate headers
     */
    @GetMapping("/items/{itemId}/download")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable String userId,
            @PathVariable String driveKey,
            @PathVariable String itemId,
            @AuthenticationPrincipal User currentUser) {
        Path filePath = myDriveService.downloadFile(userId, driveKey, itemId, currentUser);

        String contentType;
        try {
            contentType = Files.probeContentType(filePath);
        } catch (IOException e) {
            contentType = null;
        }
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        String fileName = filePath.getFileName().toString();
        String contentDisposition = ContentDisposition.attachment()
                .filename(fileName)
                .build()
                .toString();

        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                inputStream.transferTo(outputStream);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Content-Disposition", contentDisposition)
                .body(body);
    }

    /**
     * Updates properties of a drive item (file or folder).
     * Supports renaming (which regenerates item IDs for the item and all descendants)
     * and changing the display color. Both operations can be performed in a single request.
     *
     * @param userId      the owner of the drive
     * @param driveKey    the drive type identifier
     * @param itemId      the SHA-256 hash ID of the item to update
     * @param request     the update request containing optional new name and/or color
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the updated item as a {@link DriveItem} with its new ID (if renamed)
     */
    @PutMapping("/items/{itemId}")
    public ResponseEntity<DriveItem> updateItem(
            @PathVariable String userId,
            @PathVariable String driveKey,
            @PathVariable String itemId,
            @RequestBody UpdateItemRequest request,
            @AuthenticationPrincipal User currentUser) {
        DriveItem item = myDriveService.updateItem(userId, driveKey, itemId, request, currentUser);
        return ResponseEntity.ok(item);
    }

    /**
     * Moves a file or folder to a different parent folder within the same drive.
     * Validates that the move does not create a circular folder structure (e.g., moving
     * a folder into one of its own descendants). Returns 409 Conflict if an item with
     * the same name already exists in the destination folder.
     *
     * @param userId      the owner of the drive
     * @param driveKey    the drive type identifier
     * @param itemId      the SHA-256 hash ID of the item to move
     * @param request     the move request containing the destination parent folder ID
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the moved item as a {@link DriveItem} at its new location
     */
    @PutMapping("/items/{itemId}/move")
    public ResponseEntity<DriveItem> moveItem(
            @PathVariable String userId,
            @PathVariable String driveKey,
            @PathVariable String itemId,
            @Valid @RequestBody MoveRequest request,
            @AuthenticationPrincipal User currentUser) {
        DriveItem item = myDriveService.moveItem(userId, driveKey, itemId, request, currentUser);
        return ResponseEntity.ok(item);
    }

    /**
     * Deletes a file or folder from the drive.
     * For folders, recursively deletes all contents. Also removes any associated
     * metadata (colors) from the database. The drive root cannot be deleted.
     *
     * @param userId      the owner of the drive
     * @param driveKey    the drive type identifier
     * @param itemId      the SHA-256 hash ID of the item to delete
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return HTTP 204 No Content on success
     */
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable String userId,
            @PathVariable String driveKey,
            @PathVariable String itemId,
            @AuthenticationPrincipal User currentUser) {
        myDriveService.deleteItem(userId, driveKey, itemId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
