package com.dylanjohnpratt.paradise.be.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating a new folder within a drive.
 */
public record CreateFolderRequest(
    @NotBlank String name,
    @NotBlank String parentId
) {}
