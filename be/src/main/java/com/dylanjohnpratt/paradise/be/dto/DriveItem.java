package com.dylanjohnpratt.paradise.be.dto;

import java.util.List;

/**
 * Represents a file or folder within a virtual drive.
 * Used as the value type in the flat map response keyed by item ID.
 */
public record DriveItem(
    String id,
    String name,
    String type,
    String fileType,
    String size,
    String color,
    List<String> children,
    String parentId
) {}
