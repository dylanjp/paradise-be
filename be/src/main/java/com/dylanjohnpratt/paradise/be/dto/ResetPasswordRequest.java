package com.dylanjohnpratt.paradise.be.dto;

/**
 * Request DTO for admin password reset.
 * Contains only the new password (no current password verification needed for admin reset).
 */
public record ResetPasswordRequest(String newPassword) {}
