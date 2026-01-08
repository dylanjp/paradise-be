package com.dylanjohnpratt.paradise.be.dto;

import java.util.Set;

/**
 * Request DTO for creating a new user.
 * Contains username, password, and roles for the new user.
 */
public record CreateUserRequest(String username, String password, Set<String> roles) {}
