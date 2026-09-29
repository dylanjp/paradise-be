package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DocsPathProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The configured documentation roots.
 * Holds only configuration: the main DOCS_PATH folder (label "", open to every signed-in user) and the
 * optional Obsidian vaults shown as top-level "LE Docs" and "Pratt Capitol" folders (ROLE_ADMIN only).
 * Whether a root currently exists is decided on every refresh by {@link #resolveActive()}, so a root on
 * an unmounted drive comes back as soon as the drive does.
 */
@Component
public class DocsRoots {

    private static final Logger log = LoggerFactory.getLogger(DocsRoots.class);

    /** Label of the LegendaryEpics vault root folder. */
    public static final String LE_DOCS_LABEL = "LE Docs";

    /** Label of the Pratt Capitol vault root folder. */
    public static final String PRATT_CAPITOL_LABEL = "Pratt Capitol";

    /** Folder whose presence marks an Obsidian vault root. */
    static final String VAULT_MARKER = ".obsidian";

    /**
     * One configured root.
     *
     * @param label     "" for the main docs folder, otherwise the top-level folder name shown in the tree
     * @param path      the configured filesystem path, "" when unset
     * @param adminOnly true when only ROLE_ADMIN may list or read it
     */
    public record Entry(String label, String path, boolean adminOnly) {

        /** True for the main DOCS_PATH root, whose content sits directly under the tree root. */
        public boolean isMain() {
            return label.isEmpty();
        }

        /** True when a non-blank path is configured. */
        public boolean isConfigured() {
            return path != null && !path.isBlank();
        }
    }

    /**
     * A root that existed at the last refresh.
     *
     * @param label     same as {@link Entry#label()}
     * @param dir       the real (symlink/junction-resolved) absolute directory
     * @param scope     the nearest ancestor-or-self of {@code dir} containing {@code .obsidian}, else {@code dir};
     *                  embeds referenced by notes in this root may resolve anywhere inside it
     * @param adminOnly same as {@link Entry#adminOnly()}
     */
    public record ActiveRoot(String label, Path dir, Path scope, boolean adminOnly) {

        /** True for the main DOCS_PATH root. */
        public boolean isMain() {
            return label.isEmpty();
        }
    }

    private final List<Entry> entries;

    public DocsRoots(DocsPathProperties properties) {
        this.entries = List.of(
                new Entry("", clean(properties.path()), false),
                new Entry(LE_DOCS_LABEL, clean(properties.lePath()), true),
                new Entry(PRATT_CAPITOL_LABEL, clean(properties.prattCapitolPath()), true)
        );
    }

    /** All three entries, configured or not, main root first. */
    public List<Entry> entries() {
        return entries;
    }

    /** Entries with a non-blank path. */
    public List<Entry> configured() {
        return entries.stream().filter(Entry::isConfigured).toList();
    }

    /**
     * Resolves every configured entry that currently exists and is a directory.
     * Never throws; a root that cannot be resolved is skipped.
     */
    public List<ActiveRoot> resolveActive() {
        List<ActiveRoot> active = new ArrayList<>();
        for (Entry entry : configured()) {
            Optional<ActiveRoot> root = resolve(entry);
            if (root.isPresent()) {
                active.add(root.get());
            } else {
                log.warn("Docs root {} is not an existing directory; skipping it until the next refresh",
                        describe(entry));
            }
        }
        return List.copyOf(active);
    }

    /**
     * Resolves one entry to its real directory and vault scope.
     *
     * @return the active root, or empty when unset, missing, not a directory, or unreadable
     */
    public static Optional<ActiveRoot> resolve(Entry entry) {
        if (entry == null || !entry.isConfigured()) {
            return Optional.empty();
        }
        try {
            Path dir = Path.of(entry.path()).toAbsolutePath().normalize();
            if (!Files.isDirectory(dir)) {
                return Optional.empty();
            }
            Path real = dir.toRealPath();
            return Optional.of(new ActiveRoot(entry.label(), real, detectScope(real), entry.adminOnly()));
        } catch (InvalidPathException | IOException | SecurityException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns the nearest ancestor-or-self directory containing {@code .obsidian}, or {@code dir} itself
     * when no enclosing vault is found.
     */
    static Path detectScope(Path dir) {
        for (Path current = dir; current != null; current = current.getParent()) {
            try {
                if (Files.isDirectory(current.resolve(VAULT_MARKER))) {
                    return current;
                }
            } catch (SecurityException e) {
                break;
            }
        }
        return dir;
    }

    /** Human-readable name for log lines: "docs.path" or the quoted label. */
    public static String describe(Entry entry) {
        return entry.isMain() ? "docs.path" : "\"" + entry.label() + "\"";
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
