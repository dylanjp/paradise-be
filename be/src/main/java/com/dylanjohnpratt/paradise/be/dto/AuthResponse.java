package com.dylanjohnpratt.paradise.be.dto;

import java.util.Set;

/**
 * Response DTO for successful authentication.
 * Contains the JWT token, username, and user roles.
 */
public record AuthResponse(String token, String username, Set<String> roles) {}
