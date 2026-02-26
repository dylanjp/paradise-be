package com.dylanjohnpratt.paradise.be.controller;

import com.dylanjohnpratt.paradise.be.dto.CreateFolderRequest;
import com.dylanjohnpratt.paradise.be.dto.DriveItem;
import com.dylanjohnpratt.paradise.be.dto.UpdateItemRequest;
import com.dylanjohnpratt.paradise.be.exception.InvalidDriveKeyException;
import com.dylanjohnpratt.paradise.be.model.DriveKey;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.service.MyDriveService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/users/{userId}/drives/{driveKey}")
public class MyDriveController {

    private final MyDriveService myDriveService;

    public MyDriveController(MyDriveService myDriveService) {
        this.myDriveService = myDriveService;
    }

    /**
     * Lists all items in a user's drive as a flat map keyed by item ID.
     * The root folder is always included; its children array defines the top-level contents.
     *
     * @param userId   the owner of the drive
     * @param driveKey one of: myDrive, sharedDrive, adminDrive, mediaCache
     * @param currentUser the authenticated user (injected by Spring Security)
     * @return 200 OK with a {@code Map<String, DriveItem>} body
     * @throws InvalidDriveKeyException if driveKey is not a valid {@link DriveKey}
     * @throws DriveAccessDeniedException if currentUser may not access this drive
     */
    @GetMapping
    public ResponseEntity<Map<String, DriveItem>> getDriveContents(
            @PathVariable String userId,
            @PathVariable String driveKey,
            @AuthenticationPrincipal User currentUser) {
        validateDriveKey(driveKey);
        Map<String, DriveItem> contents = myDriveService.getDriveContents(userId, driveKey, currentUser);
        return ResponseEntity.ok(contents);
    }

    /**
     * Creates a new folder inside the specified drive.
     *
     * @param userId   the owner of the drive
     * @param driveKey one of: myDrive, sharedDrive, adminDrive, mediaCache
     * @param request  body containing {@code name} and {@code parentId} (both required)
     * @param currentUser the authenticated user
     * @return 201 Created with the new {@link DriveItem} representing the folder
     * @throws DriveItemNotFoundException if parentId does not exist
     * @throws DriveItemConflictException if a folder with the same name already exists under the parent
     */
    @PostMapping("/folders")
    public ResponseEntity<DriveItem> createFolder(
            @PathVariable String userId,
            @PathVariable String driveKey,
            @Valid @RequestBody CreateFolderRequest request,
            @AuthenticationPrincipal User currentUser) {
        validateDriveKey(driveKey);
        DriveItem folder = myDriveService.createFolder(userId, driveKey, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(folder);
    }

    /**
     * Uploads a file into the specified drive folder.
     * The request must be {@code multipart/form-data} with a {@code file} part and a {@code parentId} parameter.
     *
     * @param userId   the owner of the drive
     * @param driveKey one of: myDrive, sharedDrive, adminDrive, mediaCache
     * @param file     the multipart file to upload
     * @param parentId the ID of the parent folder to upload into
     * @param currentUser the authenticated user
     * @return 201 Created with the new {@link DriveItem} representing the uploaded file
     * @throws DriveItemNotFoundException if parentId does not exist
     * @throws DriveItemConflictException if a file with the same name already exists under the parent
     */
    @PostMapping("/files")
    public ResponseEntity<DriveItem> uploadFile(
            @PathVariable String userId,
            @PathVariable String driveKey,
            @RequestParam("file") MultipartFile file,
            @RequestParam String parentId,
            @AuthenticationPrincipal User currentUser) {
        validateDriveKey(driveKey);
        DriveItem item = myDriveService.uploadFile(userId, driveKey, file, parentId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    /**
     * Downloads a file from the drive. The response is streamed with the appropriate
     * Content-Type (auto-detected) and a {@code Content-Disposition: attachment} header.
     * Folders cannot be downloaded.
     *
     * @param userId   the owner of the drive
     * @param driveKey one of: myDrive, sharedDrive, adminDrive, mediaCache
     * @param itemId   the ID of the file to download
     * @param currentUser the authenticated user
     * @return 200 OK with the file bytes streamed in the response body
     * @throws DriveItemNotFoundException if itemId does not exist
     * @throws DownloadFolderException if itemId refers to a folder
     */
    @GetMapping("/items/{itemId}/download")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable String userId,
            @PathVariable String driveKey,
            @PathVariable String itemId,
            @AuthenticationPrincipal User currentUser) {
        validateDriveKey(driveKey);
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

        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                inputStream.transferTo(outputStream);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .body(body);
    }

    /**
     * Updates a drive item's name and/or color. Both fields are optional;
     * only provided fields are applied. Renaming also renames the underlying file/folder on disk.
     *
     * @param userId   the owner of the drive
     * @param driveKey one of: myDrive, sharedDrive, adminDrive, mediaCache
     * @param itemId   the ID of the item to update
     * @param request  body with optional {@code name} and/or {@code color}
     * @param currentUser the authenticated user
     * @return 200 OK with the updated {@link DriveItem}
     * @throws DriveItemNotFoundException if itemId does not exist
     * @throws DriveItemConflictException if renaming would conflict with an existing sibling
     */
    @PutMapping("/items/{itemId}")
    public ResponseEntity<DriveItem> updateItem(
            @PathVariable String userId,
            @PathVariable String driveKey,
            @PathVariable String itemId,
            @RequestBody UpdateItemRequest request,
            @AuthenticationPrincipal User currentUser) {
        validateDriveKey(driveKey);
        DriveItem item = myDriveService.updateItem(userId, driveKey, itemId, request, currentUser);
        return ResponseEntity.ok(item);
    }

    /**
     * Deletes a file or folder (and all its contents recursively) from the drive.
     * The root folder of a drive cannot be deleted.
     *
     * @param userId   the owner of the drive
     * @param driveKey one of: myDrive, sharedDrive, adminDrive, mediaCache
     * @param itemId   the ID of the item to delete
     * @param currentUser the authenticated user
     * @return 204 No Content on success
     * @throws DriveItemNotFoundException if itemId does not exist
     * @throws DriveRootDeletionException if attempting to delete the root folder
     */
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable String userId,
            @PathVariable String driveKey,
            @PathVariable String itemId,
            @AuthenticationPrincipal User currentUser) {
        validateDriveKey(driveKey);
        myDriveService.deleteItem(userId, driveKey, itemId, currentUser);
        return ResponseEntity.noContent().build();
    }

    private void validateDriveKey(String driveKey) {
        if (DriveKey.fromString(driveKey) == null) {
            throw new InvalidDriveKeyException("Invalid drive key: " + driveKey);
        }
    }
}
