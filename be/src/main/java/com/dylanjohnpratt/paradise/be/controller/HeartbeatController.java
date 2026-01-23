package com.dylanjohnpratt.paradise.be.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
