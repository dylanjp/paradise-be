package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DocsPathProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The configured documentation roots: one section per listed top-level folder of the Obsidian vault
 * (DOCS_VAULT_PATH). Each section appears in the tree as a top-level folder with the folder's own name,
 * so tree paths equal vault-relative paths ({@code Home and Family/House Documentation/...}).
 * DOCS_FOLDERS sections are open to every signed-in user; DOCS_ADMIN_FOLDERS sections are ROLE_ADMIN only.
 * <p>
 * Holds only configuration. Whether a section currently exists is decided on every refresh by
 * {@link #resolveActive()}, so a vault on an unmounted drive comes back as soon as the drive does.
 */
@Component
public class DocsRoots {

    private static final Logger log = LoggerFactory.getLogger(DocsRoots.class);

    /** Folder whose presence marks an Obsidian vault root. */
    static final String VAULT_MARKER = ".obsidian";

    /**
     * One configured section.
     *
     * @param label     the top-level folder name, shown as-is in the tree and used as the path prefix
     * @param path      the section's filesystem path (the vault path plus the folder name)
     * @param adminOnly true when only ROLE_ADMIN may list or read it
     */
    public record Entry(String label, String path, boolean adminOnly) {}

    /**
     * A section that existed at the last refresh.
     *
     * @param label     same as {@link Entry#label()}
     * @param dir       the real (symlink/junction-resolved) absolute directory
     * @param scope     the nearest ancestor-or-self of {@code dir} containing {@code .obsidian}, else {@code dir};
     *                  embeds referenced by notes in this section may resolve anywhere inside it
     * @param adminOnly same as {@link Entry#adminOnly()}
     */
    public record ActiveRoot(String label, Path dir, Path scope, boolean adminOnly) {}

    private final String vaultPath;
    private final List<Entry> entries;

    @Autowired
    public DocsRoots(DocsPathProperties properties) {
        this(properties.vaultPath(), properties.folders(), properties.adminFolders());
    }

    /**
     * @param vaultPath    the vault folder; blank or null disables the Documentation feature
     * @param folders      section folder names open to every signed-in user
     * @param adminFolders section folder names for ROLE_ADMIN only; a name in both lists is admin-only
     */
    public DocsRoots(String vaultPath, List<String> folders, List<String> adminFolders) {
        this.vaultPath = clean(vaultPath);
        this.entries = buildEntries(this.vaultPath, folders, adminFolders);
    }

    /** The configured vault path, "" when unset. */
    public String vaultPath() {
        return vaultPath;
    }

    /** Every valid configured section, admin-only ones first, each in its configured order. */
    public List<Entry> entries() {
        return entries;
    }

    /**
     * Resolves every section that currently exists and is a directory.
     * Never throws; a section that cannot be resolved is skipped.
     */
    public List<ActiveRoot> resolveActive() {
        List<ActiveRoot> active = new ArrayList<>();
        for (Entry entry : entries) {
            Optional<ActiveRoot> root = resolve(entry);
            if (root.isPresent()) {
                active.add(root.get());
            } else {
                log.warn("Docs section {} is not an existing directory; skipping it until the next refresh",
                        describe(entry));
            }
        }
        return List.copyOf(active);
    }

    /**
     * Resolves one entry to its real directory and vault scope.
     *
     * @return the active root, or empty when missing, not a directory, or unreadable
     */
    public static Optional<ActiveRoot> resolve(Entry entry) {
        if (entry == null || entry.path() == null || entry.path().isBlank()) {
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

    /** Human-readable name for log lines: the quoted label. */
    public static String describe(Entry entry) {
        return "\"" + entry.label() + "\"";
    }

    /**
     * One entry per valid folder name. Admin folders are added first, so a name listed in both lists
     * stays admin-only. Invalid names (blank, dot-names, more than one path segment, drive or stream
     * syntax) and case-insensitive duplicates are logged and skipped.
     */
    private static List<Entry> buildEntries(String vaultPath, List<String> folders, List<String> adminFolders) {
        if (vaultPath.isEmpty()) {
            return List.of();
        }
        Path vault;
        try {
            vault = Path.of(vaultPath);
        } catch (InvalidPathException e) {
            log.error("docs.vault-path is not a valid path; the Documentation feature is unavailable");
            return List.of();
        }
        Map<String, Entry> byKey = new LinkedHashMap<>();
        add(byKey, vault, adminFolders, true);
        add(byKey, vault, folders, false);
        return List.copyOf(byKey.values());
    }

    private static void add(Map<String, Entry> byKey, Path vault, List<String> names, boolean adminOnly) {
        if (names == null) {
            return;
        }
        for (String raw : names) {
            String name = clean(raw);
            if (name.isEmpty()) {
                continue;
            }
            Optional<Path> dir = sectionDir(vault, name);
            if (dir.isEmpty()) {
                log.warn("Ignoring docs folder \"{}\": it must be a single top-level folder name of the vault",
                        name);
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            Entry existing = byKey.get(key);
            if (existing != null) {
                if (existing.adminOnly() && !adminOnly) {
                    log.warn("Docs folder \"{}\" is listed as both open and admin-only; it stays admin-only", name);
                } else {
                    log.warn("Ignoring duplicate docs folder \"{}\"", name);
                }
                continue;
            }
            byKey.put(key, new Entry(name, dir.get().toString(), adminOnly));
        }
    }

    /** {@code vault/name} when {@code name} is exactly one ordinary path segment, else empty. */
    private static Optional<Path> sectionDir(Path vault, String name) {
        if (name.startsWith(".") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.indexOf(':') >= 0 || name.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        try {
            Path segment = Path.of(name);
            if (segment.getNameCount() != 1 || segment.getRoot() != null
                    || !segment.getFileName().toString().equals(name)) {
                return Optional.empty();
            }
            return Optional.of(vault.resolve(segment));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
