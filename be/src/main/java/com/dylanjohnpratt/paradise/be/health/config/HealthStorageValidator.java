package com.dylanjohnpratt.paradise.be.health.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class HealthStorageValidator {

    private static final Logger logger = LoggerFactory.getLogger(HealthStorageValidator.class);

    private final HealthStorageProperties properties;

    public HealthStorageValidator(HealthStorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        logger.info("Validating health storage configuration...");

        if (properties.documents() == null || properties.documents().isBlank()) {
            String message = "Missing or empty health storage configuration: health.storage.documents";
            logger.error(message);
            throw new IllegalStateException(message);
        }

        Path dir = Path.of(properties.documents());
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                logger.info("Created directory for health.storage.documents: {}", dir);
            } catch (IOException e) {
                logger.error("Failed to create directory for health.storage.documents: {}", dir, e);
                throw new IllegalStateException(
                        "Failed to create directory for health.storage.documents: " + dir, e);
            }
        } else {
            logger.info("Directory exists for health.storage.documents: {}", dir);
        }

        if (!Files.isWritable(dir)) {
            String message = "health.storage.documents is not writable: " + dir;
            logger.error(message);
            throw new IllegalStateException(message);
        }

        logger.info("Health storage validation completed successfully");
    }
}
