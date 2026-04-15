package com.dylanjohnpratt.paradise.be.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates docs path configuration on startup.
 * Unlike DrivePathValidator, this only logs errors/warnings because the docs feature is optional.
 */
@Component
public class DocsPathValidator {

    private static final Logger logger = LoggerFactory.getLogger(DocsPathValidator.class);

    private final DocsPathProperties docsPathProperties;

    public DocsPathValidator(DocsPathProperties docsPathProperties) {
        this.docsPathProperties = docsPathProperties;
    }

    @PostConstruct
    public void validate() {
        logger.info("Validating docs path configuration...");

        String docsPath = docsPathProperties.path();

        if (docsPath == null || docsPath.isBlank()) {
            logger.error("docs.path is not set or is empty. Documentation feature will be unavailable.");
            return;
        }

        Path dir = Path.of(docsPath);
        if (!Files.exists(dir)) {
            logger.warn("docs.path directory does not exist: {}. Documentation tree will be empty.", dir);
            return;
        }

        logger.info("Docs path validation completed successfully: {}", dir);
    }
}
