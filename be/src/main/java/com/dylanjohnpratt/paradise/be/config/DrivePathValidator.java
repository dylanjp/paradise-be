package com.dylanjohnpratt.paradise.be.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates drive path configuration on startup.
 * Ensures all required drive paths are configured and creates directories that don't exist.
 */
@Component
public class DrivePathValidator {

    private static final Logger logger = LoggerFactory.getLogger(DrivePathValidator.class);

    private final DrivePathProperties drivePathProperties;

    public DrivePathValidator(DrivePathProperties drivePathProperties) {
        this.drivePathProperties = drivePathProperties;
    }

    @PostConstruct
    public void validate() {
        logger.info("Validating drive path configuration...");

        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("drive.paths.myDrive", drivePathProperties.myDrive());
        paths.put("drive.paths.sharedDrive", drivePathProperties.sharedDrive());
        paths.put("drive.paths.adminDrive", drivePathProperties.adminDrive());
        paths.put("drive.paths.mediaCache", drivePathProperties.mediaCache());
        paths.put("drive.paths.plexUpload", drivePathProperties.plexUpload());

        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> entry : paths.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                missing.add(entry.getKey());
            }
        }

        if (!missing.isEmpty()) {
            String message = "Missing or empty drive path configuration: " + String.join(", ", missing);
            logger.error(message);
            throw new IllegalStateException(message);
        }

        for (Map.Entry<String, String> entry : paths.entrySet()) {
            Path dir = Path.of(entry.getValue());
            if (!Files.exists(dir)) {
                try {
                    Files.createDirectories(dir);
                    logger.info("Created directory for {}: {}", entry.getKey(), dir);
                } catch (IOException e) {
                    logger.error("Failed to create directory for {}: {}", entry.getKey(), dir, e);
                    throw new IllegalStateException(
                            "Failed to create directory for " + entry.getKey() + ": " + dir, e);
                }
            } else {
                logger.info("Directory exists for {}: {}", entry.getKey(), dir);
            }
        }

        logger.info("Drive path validation completed successfully");
    }
}
