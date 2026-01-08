package com.dylanjohnpratt.paradise.be.dto;

/**
 * Request DTO for changing a user's own password.
 * Requires current password for verification and new password.
 */
public record ChangePasswordRequest(String currentPassword, String newPassword) {}
