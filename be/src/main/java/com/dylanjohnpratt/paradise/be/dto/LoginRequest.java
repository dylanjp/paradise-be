package com.dylanjohnpratt.paradise.be.dto;

/**
 * Request DTO for user login.
 * Contains username and password for authentication.
 */
public record LoginRequest(String username, String password) {}
