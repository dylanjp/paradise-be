package com.dylanjohnpratt.paradise.be.config;

import com.dylanjohnpratt.paradise.be.service.DocsRoots;
import com.dylanjohnpratt.paradise.be.service.DocsRoots.ActiveRoot;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates docs path configuration on startup.
 * Unlike DrivePathValidator, this only logs errors/warnings because the docs feature is optional:
 * every root is re-checked on each refresh, so a vault on a drive that mounts later still appears.
 * Logs each configured root with its detected Obsidian vault scope (the folder embeds may resolve in).
 */
@Component
public class DocsPathValidator {

    private static final Logger logger = LoggerFactory.getLogger(DocsPathValidator.class);

    /** A vault scope further above its root than this is probably a misconfiguration. */
    private static final int MAX_SCOPE_LEVELS = 3;

    private final DocsRoots docsRoots;

    public DocsPathValidator(DocsRoots docsRoots) {
        this.docsRoots = docsRoots;
    }

    @PostConstruct
    public void validate() {
        try {
            logger.info("Validating docs path configuration...");
            if (docsRoots.configured().isEmpty()) {
                logger.error("No docs roots are set (docs.path, docs.le-path, docs.pratt-capitol-path). "
                        + "Documentation feature will be unavailable.");
                return;
            }

            List<ActiveRoot> active = new ArrayList<>();
            for (DocsRoots.Entry entry : docsRoots.entries()) {
                String name = DocsRoots.describe(entry);
                if (!entry.isConfigured()) {
                    logger.info("Docs root {} is not set; skipping it", name);
                    continue;
                }
                Optional<ActiveRoot> resolved = DocsRoots.resolve(entry);
                if (resolved.isEmpty()) {
                    logger.warn("Docs root {} directory does not exist: {}. It will be skipped until it exists.",
                            name, entry.path());
                    continue;
                }
                ActiveRoot root = resolved.get();
                active.add(root);
                logger.info("Docs root {} -> {} (vault scope: {}, adminOnly={})",
                        name, root.dir(), root.scope(), root.adminOnly());

                int levels = root.scope().equals(root.dir()) ? 0 : root.scope().relativize(root.dir()).getNameCount();
                if (levels > MAX_SCOPE_LEVELS) {
                    logger.warn("Docs root {} has its vault scope {} levels above it ({}). "
                            + "Embeds may resolve anywhere in that folder; check for a stray .obsidian folder.",
                            name, levels, root.scope());
                }
                if (entry.isMain()) {
                    warnOnShadowedFolders(root.dir());
                }
            }
            warnOnOverlap(active);
        } catch (RuntimeException e) {
            logger.warn("Docs path validation failed: {}", e.getMessage());
        }
    }

    /** A DOCS_PATH folder named like a vault root would be confused with (or shadowed by) that root. */
    private void warnOnShadowedFolders(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String entryName = entry.getFileName().toString();
                for (String label : List.of(DocsRoots.LE_DOCS_LABEL, DocsRoots.PRATT_CAPITOL_LABEL)) {
                    if (entryName.equalsIgnoreCase(label)) {
                        logger.warn("docs.path contains a top-level entry \"{}\" that clashes with the \"{}\" "
                                + "vault folder; rename it", entryName, label);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            logger.warn("Unable to list docs.path: {}", e.getMessage());
        }
    }

    /** Admin-only vault content inside an open root or its vault scope is hidden from non-admins; say so. */
    private void warnOnOverlap(List<ActiveRoot> active) {
        for (ActiveRoot open : active) {
            if (open.adminOnly()) {
                continue;
            }
            for (ActiveRoot restricted : active) {
                if (!restricted.adminOnly()) {
                    continue;
                }
                if (restricted.dir().startsWith(open.scope()) || open.dir().startsWith(restricted.dir())) {
                    logger.warn("Admin-only docs root \"{}\" overlaps docs.path or its vault scope; "
                            + "its files stay hidden from non-admins", restricted.label());
                }
            }
        }
    }
}
