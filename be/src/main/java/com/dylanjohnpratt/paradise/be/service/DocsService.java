package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.dto.DocsTreeNode;
import com.dylanjohnpratt.paradise.be.exception.DocsFileNotFoundException;
import com.dylanjohnpratt.paradise.be.exception.DocsFileTooLargeException;
import com.dylanjohnpratt.paradise.be.exception.DocsInvalidFileTypeException;
import com.dylanjohnpratt.paradise.be.exception.DocsPathTraversalException;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.service.DocsRoots.ActiveRoot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for browsing and retrieving documentation files across the configured roots
 * (DOCS_PATH, plus the admin-only "LE Docs" and "Pratt Capitol" Obsidian vaults).
 * <p>
 * Builds and caches a {@link DocsSnapshot} (tree + embed name index), filters the tree per user,
 * serves text documents (md, canvas) and binary previews (pdf, images), and resolves Obsidian embeds
 * the way Obsidian does: relative to the note's folders first, then by file name anywhere in the vault.
 * <p>
 * Every user-supplied path is checked lexically before any filesystem call, then again on its real
 * (symlink/junction-resolved) path. Dot-entries ({@code .obsidian}, {@code .trash}) are never listed or served.
 */
@Service
public class DocsService {

    private static final Logger log = LoggerFactory.getLogger(DocsService.class);

    /** Largest text document served by {@code /docs/file}. */
    static final long MAX_TEXT_BYTES = 32L * 1024 * 1024;

    /** A refresh requested within this long of the last one returns the current snapshot. */
    static final Duration REFRESH_THROTTLE = Duration.ofSeconds(10);

    private static final int MAX_REFERENCE_CACHE_ENTRIES = 1024;
    private static final Pattern DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:");
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private static final Comparator<DocsTreeNode> TREE_ORDER = Comparator
            .comparing((DocsTreeNode n) -> "file".equals(n.type()) ? 1 : 0)
            .thenComparing(DocsTreeNode::name, String.CASE_INSENSITIVE_ORDER);

    /** Which extensions a request may target. */
    private enum Kind {
        TEXT(DocsFileTypes::isText, "Only Markdown (.md) and Canvas (.canvas) files are served as text"),
        BINARY(DocsFileTypes::isBinary, "Only PDF and image files are served raw"),
        ANY(DocsFileTypes::isAllowed, "File type is not served");

        final Predicate<String> accepts;
        final String rejection;

        Kind(Predicate<String> accepts, String rejection) {
            this.accepts = accepts;
            this.rejection = rejection;
        }
    }

    /**
     * A resolved, readable docs file.
     *
     * @param root              the root the file was reached through
     * @param path              the real path of the file
     * @param scopeRelativePath the path relative to the root's vault scope, with {@code /} separators (for logs)
     */
    public record ResolvedFile(ActiveRoot root, Path path, String scopeRelativePath) {

        /** The real file name. */
        public String fileName() {
            return path.getFileName().toString();
        }
    }

    /** A text document and its explicit media type. */
    public record TextFile(String content, MediaType mediaType) {}

    private record Route(ActiveRoot root, String rest) {}

    private record LexicalPath(ActiveRoot root, Path path) {}

    private record ReferenceCacheEntry(FileTime lastModified, long size, Set<String> keys) {}

    private final DocsRoots docsRoots;
    private final DocsCacheManager docsCacheManager;
    private final Clock clock;
    private final BiFunction<String, Boolean, Set<String>> referenceExtractor;

    private final Object refreshLock = new Object();
    private CompletableFuture<DocsSnapshot> inFlightRefresh; // guarded by refreshLock

    private final Map<Path, ReferenceCacheEntry> referenceCache = new ConcurrentHashMap<>();

    @Autowired
    public DocsService(DocsRoots docsRoots, DocsCacheManager docsCacheManager) {
        this(docsRoots, docsCacheManager, Clock.systemUTC());
    }

    DocsService(DocsRoots docsRoots, DocsCacheManager docsCacheManager, Clock clock) {
        this(docsRoots, docsCacheManager, clock, DocsReferenceExtractor::referenceKeys);
    }

    /** Test seam: {@code referenceExtractor} stands in for {@link DocsReferenceExtractor#referenceKeys}. */
    DocsService(DocsRoots docsRoots, DocsCacheManager docsCacheManager, Clock clock,
                BiFunction<String, Boolean, Set<String>> referenceExtractor) {
        this.docsRoots = docsRoots;
        this.docsCacheManager = docsCacheManager;
        this.clock = clock;
        this.referenceExtractor = referenceExtractor;
    }

    // -----------------------------------------------------------------------
    // Tree
    // -----------------------------------------------------------------------

    /**
     * Returns the cached tree, building it on first use.
     * Admin-only root folders are removed for users without ROLE_ADMIN.
     */
    public DocsTreeNode getFileTree(User user) {
        DocsTreeNode full = snapshot().fullTree();
        if (isAdmin(user)) {
            return full;
        }
        Set<String> adminOnlyLabels = docsRoots.entries().stream()
                .filter(DocsRoots.Entry::adminOnly)
                .map(DocsRoots.Entry::label)
                .collect(Collectors.toSet());
        List<DocsTreeNode> visible = full.children().stream()
                .filter(n -> !(Boolean.TRUE.equals(n.root()) && adminOnlyLabels.contains(n.name())))
                .toList();
        return new DocsTreeNode(full.name(), full.type(), full.path(), visible, null);
    }

    // -----------------------------------------------------------------------
    // Refresh
    // -----------------------------------------------------------------------

    /**
     * Rebuilds the snapshot. Single-flight: concurrent callers share the in-flight build.
     * Throttled: within {@link #REFRESH_THROTTLE} after the last build, returns the current snapshot.
     * A negative elapsed time (the wall clock was stepped back) counts as expired.
     * On failure the previous snapshot is kept.
     */
    public DocsSnapshot refresh() {
        CompletableFuture<DocsSnapshot> future;
        boolean owner = false;
        synchronized (refreshLock) {
            Optional<DocsSnapshot> current = docsCacheManager.get();
            Instant last = docsCacheManager.getLastRefresh();
            if (current.isPresent() && last != null) {
                Duration elapsed = Duration.between(last, clock.instant());
                if (!elapsed.isNegative() && elapsed.compareTo(REFRESH_THROTTLE) < 0) {
                    return current.get();
                }
            }
            if (inFlightRefresh == null) {
                inFlightRefresh = new CompletableFuture<>();
                owner = true;
            }
            future = inFlightRefresh;
        }
        if (owner) {
            try {
                DocsSnapshot built = buildSnapshot();
                docsCacheManager.put(built, clock.instant());
                referenceCache.clear();
                log.info("Documentation cache refreshed ({} root(s))", built.roots().size());
                future.complete(built);
            } catch (RuntimeException e) {
                log.error("Failed to refresh documentation cache; retaining previous snapshot", e);
                future.complete(docsCacheManager.get().orElseGet(DocsSnapshot::empty));
            } catch (Error e) {
                future.completeExceptionally(e);
                throw e;
            } finally {
                synchronized (refreshLock) {
                    inFlightRefresh = null;
                }
            }
        }
        return future.join();
    }

    /**
     * Scheduled refresh; the interval comes from {@code docs.refresh-millis} (default 1 hour).
     */
    @Scheduled(fixedRateString = "${docs.refresh-millis:3600000}")
    public void scheduledRefresh() {
        refresh();
    }

    private DocsSnapshot snapshot() {
        return docsCacheManager.get().orElseGet(this::refresh);
    }

    // -----------------------------------------------------------------------
    // File access
    // -----------------------------------------------------------------------

    /**
     * Reads a text document (md or canvas).
     *
     * @throws DocsPathTraversalException   if the path escapes its root (403)
     * @throws DocsFileNotFoundException    if missing, hidden, or not accessible to the user (404)
     * @throws DocsInvalidFileTypeException if not md/canvas (400)
     * @throws DocsFileTooLargeException    if larger than 32 MB (413)
     */
    public TextFile getTextFile(String path, User user) {
        ResolvedFile file = resolveTreeFile(path, user, Kind.TEXT, snapshot());
        String content = readText(file.path());
        return new TextFile(content, DocsFileTypes.mediaType(file.fileName()));
    }

    /**
     * Resolves a binary tree file (pdf or image) for streaming.
     *
     * @throws DocsPathTraversalException   if the path escapes its root (403)
     * @throws DocsFileNotFoundException    if missing, hidden, or not accessible to the user (404)
     * @throws DocsInvalidFileTypeException if not a pdf or supported image (400)
     */
    public ResolvedFile getBinaryFile(String path, User user) {
        return resolveTreeFile(path, user, Kind.BINARY, snapshot());
    }

    /**
     * Resolves any listed file type. Used by tests; the endpoints use the typed variants.
     */
    ResolvedFile resolveTreePath(String path, User user) {
        return resolveTreeFile(path, user, Kind.ANY, snapshot());
    }

    /**
     * Resolves an Obsidian embed made by the text document {@code from}.
     * <ol>
     *   <li>{@code from} must be a readable md/canvas tree file.</li>
     *   <li>The target is cleaned by {@link DocsEmbedTargetCleaner} and must be a pdf or image.</li>
     *   <li>Reference gate: {@code from} must actually reference the target.</li>
     *   <li>Ancestor walk: the target relative to each folder from {@code from}'s folder up to the vault scope.</li>
     *   <li>Name index: files with the same name in the scope (suffix-matched when the target has folders),
     *       closest to {@code from} first.</li>
     * </ol>
     *
     * @throws DocsFileNotFoundException    if nothing matches, or the target is unsafe or unreferenced (404)
     * @throws DocsInvalidFileTypeException if {@code from} is not md/canvas or the target is not pdf/image (400)
     */
    public ResolvedFile resolveEmbed(String from, String target, boolean literal, User user) {
        DocsSnapshot snap = snapshot();
        ResolvedFile source = resolveTreeFile(from, user, Kind.TEXT, snap);
        String cleaned = DocsEmbedTargetCleaner.clean(target, literal);
        String targetKey = DocsFileTypes.key(cleaned);
        if (!referenceKeys(source).contains(targetKey)) {
            throw new DocsFileNotFoundException("Embedded file not found: " + cleaned);
        }

        ActiveRoot root = source.root();
        Path scope = root.scope();
        Path fromDir = source.path().getParent();
        Path targetPath;
        try {
            targetPath = Path.of(cleaned).normalize();
        } catch (InvalidPathException e) {
            throw new DocsFileNotFoundException("Embedded file not found: " + cleaned);
        }

        // 1. Ancestor walk: the note's folder, then each parent up to the vault scope.
        for (Path dir = fromDir; dir != null && dir.startsWith(scope); dir = dir.getParent()) {
            Optional<Path> hit = checkEmbedCandidate(dir.resolve(targetPath).normalize(), scope, user, snap);
            if (hit.isPresent()) {
                return new ResolvedFile(root, hit.get(), relative(scope, hit.get()));
            }
        }

        // 2. Name index: same file name anywhere in the vault scope, closest to the note first.
        Path fileName = targetPath.getFileName();
        if (fileName != null) {
            List<Path> candidates = snap.embedIndex().getOrDefault(root, Map.of())
                    .getOrDefault(DocsFileTypes.key(fileName.toString()), List.of());
            String suffixKey = DocsFileTypes.key(targetPath.toString());
            boolean hasFolders = targetPath.getNameCount() > 1;
            List<String> fromSegments = segmentKeys(scope.relativize(fromDir));
            List<Path> ranked = candidates.stream()
                    .filter(c -> !hasFolders || matchesSuffix(scope, c, suffixKey))
                    .sorted(proximityOrder(scope, fromSegments))
                    .toList();
            for (Path candidate : ranked) {
                Optional<Path> hit = checkEmbedCandidate(candidate, scope, user, snap);
                if (hit.isPresent()) {
                    return new ResolvedFile(root, hit.get(), relative(scope, hit.get()));
                }
            }
        }

        throw new DocsFileNotFoundException("Embedded file not found: " + cleaned);
    }

    // -----------------------------------------------------------------------
    // Path resolution
    // -----------------------------------------------------------------------

    private ResolvedFile resolveTreeFile(String path, User user, Kind kind, DocsSnapshot snap) {
        LexicalPath lexical = lexicalTreePath(path, user, snap);
        Path fileName = lexical.path().getFileName();
        if (fileName == null || !kind.accepts.test(fileName.toString())) {
            throw new DocsInvalidFileTypeException(kind.rejection);
        }
        return existingTreeFile(lexical, path, user, kind, snap);
    }

    /**
     * Lexical checks, before any filesystem call: rejects blank, NUL, rooted and drive-prefixed input,
     * routes to a root, requires the normalized path to stay inside it (403 otherwise), and hides
     * dot-entries (404).
     */
    private LexicalPath lexicalTreePath(String path, User user, DocsSnapshot snap) {
        try {
            checkRelative(path);
            Route route = route(path, user, snap);
            checkRelative(route.rest());
            if (Path.of(route.rest()).getRoot() != null) {
                throw new DocsPathTraversalException("Access denied: path is outside the documentation directory");
            }
            Path dir = route.root().dir();
            Path resolved = dir.resolve(route.rest()).normalize();
            if (!resolved.startsWith(dir)) {
                throw new DocsPathTraversalException("Access denied: path is outside the documentation directory");
            }
            if (hasHiddenElement(dir.relativize(resolved))) {
                throw notFound(path);
            }
            return new LexicalPath(route.root(), resolved);
        } catch (InvalidPathException e) {
            throw notFound(path);
        }
    }

    /** Filesystem checks: regular file, real path inside the real root, not hidden, visible to the user. */
    private ResolvedFile existingTreeFile(LexicalPath lexical, String requested, User user, Kind kind,
                                          DocsSnapshot snap) {
        ActiveRoot root = lexical.root();
        try {
            if (!Files.isRegularFile(lexical.path())) {
                throw notFound(requested);
            }
            Path real = lexical.path().toRealPath();
            if (!real.startsWith(root.dir())
                    || hasHiddenElement(root.dir().relativize(real))
                    || !kind.accepts.test(real.getFileName().toString())
                    || !visibleTo(real, user, snap)) {
                throw notFound(requested);
            }
            return new ResolvedFile(root, real, relative(root.scope(), real));
        } catch (IOException | InvalidPathException | SecurityException e) {
            throw notFound(requested);
        }
    }

    /**
     * Routes a tree path: a first segment exactly equal to a configured extra root's label goes to that
     * root (404 when it is admin-only and the user is not an admin, or when it is not currently mounted);
     * anything else goes to the main root.
     */
    private Route route(String path, User user, DocsSnapshot snap) {
        int slash = path.indexOf('/');
        String first = slash >= 0 ? path.substring(0, slash) : path;
        for (DocsRoots.Entry entry : docsRoots.configured()) {
            if (entry.isMain() || !entry.label().equals(first)) {
                continue;
            }
            if (entry.adminOnly() && !isAdmin(user)) {
                throw notFound(path);
            }
            ActiveRoot root = snap.root(entry.label()).orElseThrow(() -> notFound(path));
            return new Route(root, slash >= 0 ? path.substring(slash + 1) : "");
        }
        ActiveRoot main = snap.root("").orElseThrow(() -> notFound(path));
        return new Route(main, path);
    }

    /** String-only checks, safe to run before routing: blank/NUL are 404, rooted or drive-prefixed is 403. */
    private static void checkRelative(String path) {
        if (path == null || path.isBlank() || path.indexOf('\0') >= 0) {
            throw notFound(path);
        }
        if (path.startsWith("/") || path.startsWith("\\") || DRIVE_PREFIX.matcher(path).find()) {
            throw new DocsPathTraversalException("Access denied: path is outside the documentation directory");
        }
    }

    /**
     * Checks one embed candidate: lexically inside the scope and not hidden, then a regular file whose
     * real path is inside the real scope, not hidden, a binary type, and visible to the user.
     */
    private Optional<Path> checkEmbedCandidate(Path candidate, Path scope, User user, DocsSnapshot snap) {
        try {
            if (!candidate.startsWith(scope) || hasHiddenElement(scope.relativize(candidate))) {
                return Optional.empty();
            }
            Path name = candidate.getFileName();
            if (name == null || !DocsFileTypes.isBinary(name.toString()) || !Files.isRegularFile(candidate)) {
                return Optional.empty();
            }
            Path real = candidate.toRealPath();
            if (!real.startsWith(scope)
                    || hasHiddenElement(scope.relativize(real))
                    || !DocsFileTypes.isBinary(real.getFileName().toString())
                    || !visibleTo(real, user, snap)) {
                return Optional.empty();
            }
            return Optional.of(real);
        } catch (IOException | InvalidPathException | SecurityException e) {
            return Optional.empty();
        }
    }

    private static boolean matchesSuffix(Path scope, Path candidate, String suffixKey) {
        String key = DocsFileTypes.key(scope.relativize(candidate).toString());
        return key.equals(suffixKey) || key.endsWith("/" + suffixKey);
    }

    /** Most folders shared with the note's folder first, then fewest segments, then lexical. */
    private static Comparator<Path> proximityOrder(Path scope, List<String> fromSegments) {
        return Comparator
                .comparingInt((Path c) -> -sharedPrefix(segmentKeys(scope.relativize(c.getParent())), fromSegments))
                .thenComparingInt(c -> scope.relativize(c).getNameCount())
                .thenComparing(c -> DocsFileTypes.key(scope.relativize(c).toString()));
    }

    private static int sharedPrefix(List<String> a, List<String> b) {
        int n = Math.min(a.size(), b.size());
        int i = 0;
        while (i < n && a.get(i).equals(b.get(i))) {
            i++;
        }
        return i;
    }

    private static List<String> segmentKeys(Path relative) {
        List<String> keys = new ArrayList<>();
        for (Path element : relative) {
            String s = element.toString();
            if (!s.isEmpty()) {
                keys.add(DocsFileTypes.key(s));
            }
        }
        return keys;
    }

    /** Hides admin-only vault content from non-admins even when it sits inside an open root or scope. */
    private static boolean visibleTo(Path real, User user, DocsSnapshot snap) {
        if (isAdmin(user)) {
            return true;
        }
        return snap.roots().stream()
                .filter(ActiveRoot::adminOnly)
                .noneMatch(r -> real.startsWith(r.dir()));
    }

    static boolean isAdmin(User user) {
        return user != null && user.getAuthorities().stream()
                .anyMatch(auth -> ADMIN_ROLE.equals(auth.getAuthority()));
    }

    private static boolean hasHiddenElement(Path relative) {
        for (Path element : relative) {
            if (element.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static String relative(Path base, Path path) {
        return base.relativize(path).toString().replace('\\', '/');
    }

    private static DocsFileNotFoundException notFound(String path) {
        return new DocsFileNotFoundException("Documentation file not found: " + (path == null ? "" : path));
    }

    // -----------------------------------------------------------------------
    // Reading and reference gate
    // -----------------------------------------------------------------------

    /** Reads at most 32 MB as UTF-8 (malformed bytes become U+FFFD), dropping a leading BOM. */
    private static String readText(Path file) {
        byte[] bytes;
        try (InputStream in = Files.newInputStream(file)) {
            bytes = in.readNBytes((int) MAX_TEXT_BYTES + 1);
        } catch (IOException e) {
            throw new DocsFileNotFoundException("Unable to read documentation file: " + file.getFileName());
        }
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new DocsFileTooLargeException("Documentation file is larger than 32 MB: " + file.getFileName());
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    /**
     * The reference gate set of a text document, cached by (real path, last modified, size).
     * A document whose scan overflows the stack gets an empty set, cached like any other, so it is not
     * re-read and re-scanned on every embed request.
     */
    private Set<String> referenceKeys(ResolvedFile source) {
        Path real = source.path();
        try {
            BasicFileAttributes attrs = Files.readAttributes(real, BasicFileAttributes.class);
            ReferenceCacheEntry cached = referenceCache.get(real);
            if (cached != null && cached.lastModified().equals(attrs.lastModifiedTime())
                    && cached.size() == attrs.size()) {
                return cached.keys();
            }
            Set<String> keys;
            if (attrs.size() > MAX_TEXT_BYTES) {
                keys = Set.of();
            } else {
                String content = readText(real);
                try {
                    keys = referenceExtractor.apply(content,
                            "canvas".equals(DocsFileTypes.extension(source.fileName())));
                } catch (StackOverflowError e) {
                    log.warn("Reference scan of docs file {} overflowed the stack; none of its embeds are served",
                            source.scopeRelativePath());
                    keys = Set.of();
                }
            }
            if (referenceCache.size() >= MAX_REFERENCE_CACHE_ENTRIES) {
                referenceCache.clear();
            }
            referenceCache.put(real, new ReferenceCacheEntry(attrs.lastModifiedTime(), attrs.size(), keys));
            return keys;
        } catch (IOException | DocsFileTooLargeException e) {
            return Set.of();
        }
    }

    // -----------------------------------------------------------------------
    // Snapshot building
    // -----------------------------------------------------------------------

    private DocsSnapshot buildSnapshot() {
        List<ActiveRoot> roots = docsRoots.resolveActive();
        List<Path> adminOnlyDirs = roots.stream().filter(ActiveRoot::adminOnly).map(ActiveRoot::dir).toList();
        Set<String> extraLabels = docsRoots.configured().stream()
                .filter(e -> !e.isMain())
                .map(DocsRoots.Entry::label)
                .collect(Collectors.toSet());

        List<DocsTreeNode> topLevel = new ArrayList<>();
        for (ActiveRoot root : roots) {
            List<Path> excluded = root.adminOnly() ? List.of() : adminOnlyDirs;
            List<DocsTreeNode> children;
            if (isInside(root.dir(), excluded)) {
                log.warn("Docs root {} lies inside an admin-only root; its content is listed only there",
                        root.isMain() ? "docs.path" : root.label());
                children = List.of();
            } else {
                Set<Path> visited = new HashSet<>();
                visited.add(root.dir());
                String prefix = root.isMain() ? "" : root.label() + "/";
                children = buildChildren(root.dir(), root.dir(), prefix, excluded, visited);
            }
            if (root.isMain()) {
                // A top-level folder named like a configured vault root is shadowed by routing.
                children.stream()
                        .filter(n -> !extraLabels.contains(n.name()))
                        .forEach(topLevel::add);
            } else {
                topLevel.add(new DocsTreeNode(root.label(), "folder", root.label(), children, Boolean.TRUE));
            }
        }
        topLevel.sort(TREE_ORDER);
        DocsTreeNode tree = new DocsTreeNode("", "folder", "", List.copyOf(topLevel), null);
        return new DocsSnapshot(tree, roots, buildEmbedIndexes(roots));
    }

    /**
     * Recursively lists allowed files under {@code current}. Skips dot-entries, subdirectories whose real
     * path leaves the real root or was already visited (junction/symlink loops), and admin-only roots
     * nested in an open root. Empty folders are pruned; folders sort first, then case-insensitive names.
     */
    private List<DocsTreeNode> buildChildren(Path current, Path rootDir, String prefix, List<Path> excluded,
                                             Set<Path> visited) {
        List<DocsTreeNode> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (name.startsWith(".")) {
                    continue;
                }
                try {
                    if (Files.isDirectory(entry)) {
                        Path real = entry.toRealPath();
                        if (!real.startsWith(rootDir) || isInside(real, excluded) || !visited.add(real)) {
                            continue;
                        }
                        List<DocsTreeNode> sub = buildChildren(entry, rootDir, prefix, excluded, visited);
                        if (!sub.isEmpty()) {
                            children.add(new DocsTreeNode(name, "folder", prefix + relative(rootDir, entry), sub, null));
                        }
                    } else if (DocsFileTypes.isAllowed(name) && Files.isRegularFile(entry)) {
                        if (Files.isSymbolicLink(entry)) {
                            Path real = entry.toRealPath();
                            if (!real.startsWith(rootDir) || isInside(real, excluded)
                                    || !DocsFileTypes.isAllowed(real.getFileName().toString())) {
                                continue;
                            }
                        }
                        children.add(new DocsTreeNode(name, "file", prefix + relative(rootDir, entry), null, null));
                    }
                } catch (IOException | SecurityException e) {
                    log.debug("Skipping unreadable docs entry {}: {}", name, e.getMessage());
                }
            }
        } catch (IOException | DirectoryIteratorException | SecurityException e) {
            log.warn("Error reading docs directory {}: {}", relative(rootDir, current), e.getMessage());
        }
        children.sort(TREE_ORDER);
        return List.copyOf(children);
    }

    private static boolean isInside(Path path, List<Path> dirs) {
        for (Path dir : dirs) {
            if (path.startsWith(dir)) {
                return true;
            }
        }
        return false;
    }

    /** Builds one name index per distinct scope and maps every root to its scope's index. */
    private Map<ActiveRoot, Map<String, List<Path>>> buildEmbedIndexes(List<ActiveRoot> roots) {
        Map<Path, Map<String, List<Path>>> byScope = new HashMap<>();
        Map<ActiveRoot, Map<String, List<Path>>> result = new LinkedHashMap<>();
        for (ActiveRoot root : roots) {
            result.put(root, byScope.computeIfAbsent(root.scope(), DocsService::indexScope));
        }
        return Map.copyOf(result);
    }

    /**
     * Walks a vault scope and maps {@link DocsFileTypes#key(String)} of every pdf/image name to its paths.
     * Dot-folders are skipped, as are directories whose real path leaves the scope or was already visited.
     */
    static Map<String, List<Path>> indexScope(Path scope) {
        Map<String, List<Path>> index = new HashMap<>();
        Set<Path> visited = new HashSet<>();
        try {
            Files.walkFileTree(scope, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(scope)) {
                        Path name = dir.getFileName();
                        if (name == null || name.toString().startsWith(".")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    try {
                        Path real = dir.toRealPath();
                        if (!real.startsWith(scope) || !visited.add(real)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    } catch (IOException | SecurityException e) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path name = file.getFileName();
                    if (name != null && !name.toString().startsWith(".")
                            && (attrs.isRegularFile() || attrs.isSymbolicLink())
                            && DocsFileTypes.isBinary(name.toString())) {
                        index.computeIfAbsent(DocsFileTypes.key(name.toString()), k -> new ArrayList<>()).add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | SecurityException e) {
            log.warn("Error indexing docs vault scope: {}", e.getMessage());
        }
        Map<String, List<Path>> frozen = new HashMap<>();
        index.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        return Map.copyOf(frozen);
    }
}
