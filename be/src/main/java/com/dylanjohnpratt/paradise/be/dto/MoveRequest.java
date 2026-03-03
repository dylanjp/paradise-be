package com.dylanjohnpratt.paradise.be.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for moving a file or folder to a new parent within a drive.
 */
public record MoveRequest(
    @NotBlank String parentId
) {}
