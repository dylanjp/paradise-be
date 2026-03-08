package com.dylanjohnpratt.paradise.be.controller;

import com.dylanjohnpratt.paradise.be.dto.PlexUploadResponse;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.service.MyDriveService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for uploading media files to the Plex media server staging directory.
 * Provides a dedicated upload endpoint separate from the general drive file management,
 * allowing authenticated users to place files directly into the Plex ingest folder.
 */
@RestController
@RequestMapping("/users/{userId}/plex")
public class PlexUploadController {

    private final MyDriveService myDriveService;

    public PlexUploadController(MyDriveService myDriveService) {
        this.myDriveService = myDriveService;
    }

    /**
     * Uploads a file to the Plex media server staging directory.
     * The file is saved to the configured plexUpload path on the server filesystem.
     * Rejects uploads if a file with the same name already exists in the staging directory.
     *
     * @param userId      the ID of the user performing the upload (must match the authenticated user)
     * @param file        the multipart file to upload
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the uploaded file's name and formatted size
     */
    @PostMapping("/upload")
    public ResponseEntity<PlexUploadResponse> uploadToPlex(
            @PathVariable String userId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        PlexUploadResponse response = myDriveService.uploadToPlex(userId, file, currentUser);
        return ResponseEntity.ok(response);
    }
}
