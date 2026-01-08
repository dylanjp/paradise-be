package com.dylanjohnpratt.paradise.be.dto;

import java.util.Set;

/**
 * Request DTO for updating a user's roles.
 * Contains the new set of roles to assign to the user.
 */
public record UpdateRolesRequest(Set<String> roles) {}
