package com.dylanjohnpratt.paradise.be.controller;

import com.dylanjohnpratt.paradise.be.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller providing a health check endpoint.
 * Allows the frontend to verify the backend service is running and responsive.
 */
@RestController
@RequestMapping("/heartbeat")
public class HeartbeatController {

    /**
     * Health check endpoint that returns HTTP 200 OK when the service is operational.
     *
     * @return ResponseEntity with 200 OK status and no body
     */
    @GetMapping
    public ResponseEntity<Void> heartbeat() {
        return ResponseEntity.ok().build();
    }

    /**
     * Debug endpoint that returns the current user's authentication info.
     * Useful for debugging authentication issues.
     *
     * @param currentUser the authenticated user (or null if not authenticated)
     * @return ResponseEntity with user info
     */
    @GetMapping("/whoami")
    public ResponseEntity<Map<String, Object>> whoami(@AuthenticationPrincipal User currentUser) {
        Map<String, Object> info = new HashMap<>();
        if (currentUser != null) {
            info.put("authenticated", true);
            info.put("id", currentUser.getId());
            info.put("username", currentUser.getUsername());
            info.put("roles", currentUser.getRoles());
            info.put("authorities", currentUser.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .toList());
        } else {
            info.put("authenticated", false);
        }
        return ResponseEntity.ok(info);
    }
}
