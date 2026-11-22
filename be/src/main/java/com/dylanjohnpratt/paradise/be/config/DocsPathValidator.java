package com.dylanjohnpratt.paradise.be.config;

import com.dylanjohnpratt.paradise.be.service.DocsRoots;
import com.dylanjohnpratt.paradise.be.service.DocsRoots.ActiveRoot;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Validates the docs configuration on startup.
 * Unlike DrivePathValidator, this only logs errors/warnings because the docs feature is optional:
 * every section is re-checked on each refresh, so a vault on a drive that mounts later still appears.
 * Logs each configured section with its detected Obsidian vault scope (the folder embeds may resolve in).
 */
@Component
public class DocsPathValidator {

    private static final Logger logger = LoggerFactory.getLogger(DocsPathValidator.class);

    private final DocsRoots docsRoots;

    public DocsPathValidator(DocsRoots docsRoots) {
        this.docsRoots = docsRoots;
    }

    @PostConstruct
    public void validate() {
        try {
            logger.info("Validating docs configuration...");
            if (docsRoots.vaultPath().isEmpty()) {
                logger.error("docs.vault-path (DOCS_VAULT_PATH) is not set. Documentation feature will be unavailable.");
                return;
            }
            if (docsRoots.entries().isEmpty()) {
                logger.error("No docs sections are set (docs.folders, docs.admin-folders). "
                        + "Documentation feature will be unavailable.");
                return;
            }

            for (DocsRoots.Entry entry : docsRoots.entries()) {
                String name = DocsRoots.describe(entry);
                Optional<ActiveRoot> resolved = DocsRoots.resolve(entry);
                if (resolved.isEmpty()) {
                    logger.warn("Docs section {} folder does not exist: {}. It will be skipped until it exists.",
                            name, entry.path());
                    continue;
                }
                ActiveRoot root = resolved.get();
                logger.info("Docs section {} -> {} (vault scope: {}, adminOnly={})",
                        name, root.dir(), root.scope(), root.adminOnly());
                if (root.scope().equals(root.dir())) {
                    logger.warn("Docs section {} is not inside an Obsidian vault (no .obsidian folder above it); "
                            + "embeds resolve only inside the section", name);
                }
            }
        } catch (RuntimeException e) {
            logger.warn("Docs configuration validation failed: {}", e.getMessage());
        }
    }
}
