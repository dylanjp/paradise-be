package com.dylanjohnpratt.paradise.be.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for HeartbeatController.
 * 
 * Validates: Requirements 1.1, 1.2, 2.1, 2.2
 * - GET /heartbeat returns HTTP 200 OK
 * - Endpoint is accessible without authentication
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HeartbeatControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("GET /heartbeat returns 200 OK without authentication")
    void heartbeat_returnsOkWithoutAuthentication() {
        ResponseEntity<Void> response = restTemplate.getForEntity("/heartbeat", Void.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
