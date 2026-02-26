package com.dylanjohnpratt.paradise.be.controller;

import com.dylanjohnpratt.paradise.be.dto.PlexUploadResponse;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.service.MyDriveService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users/{userId}/plex")
public class PlexUploadController {

    private final MyDriveService myDriveService;

    public PlexUploadController(MyDriveService myDriveService) {
        this.myDriveService = myDriveService;
    }

    @PostMapping("/upload")
    public ResponseEntity<PlexUploadResponse> uploadToPlex(
            @PathVariable String userId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        PlexUploadResponse response = myDriveService.uploadToPlex(userId, file, currentUser);
        return ResponseEntity.ok(response);
    }
}
