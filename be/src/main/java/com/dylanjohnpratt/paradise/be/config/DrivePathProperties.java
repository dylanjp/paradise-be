package com.dylanjohnpratt.paradise.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for drive filesystem path mappings.
 * Bound from {@code drive.paths.*} properties in application.properties.
 * Each property specifies the absolute filesystem path on the server where
 * the corresponding drive type stores its files.
 *
 * @param myDrive     base directory for per-user personal drives (each user gets a subdirectory)
 * @param sharedDrive directory for files shared across all users
 * @param adminDrive  directory accessible only to admin users
 * @param mediaCache  read-only directory for cached media content
 * @param plexUpload  staging directory for files uploaded to the Plex media server
 */
@ConfigurationProperties(prefix = "drive.paths")
public record DrivePathProperties(
    String myDrive,
    String sharedDrive,
    String adminDrive,
    String mediaCache,
    String plexUpload
) {}
