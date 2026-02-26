package com.dylanjohnpratt.paradise.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "drive.paths")
public record DrivePathProperties(
    String myDrive,
    String sharedDrive,
    String adminDrive,
    String mediaCache,
    String plexUpload
) {}
