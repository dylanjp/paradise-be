package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DocsPathProperties;
import com.dylanjohnpratt.paradise.be.dto.DocsTreeNode;
import com.dylanjohnpratt.paradise.be.exception.DocsFileNotFoundException;
import com.dylanjohnpratt.paradise.be.exception.DocsInvalidFileTypeException;
import com.dylanjohnpratt.paradise.be.exception.DocsPathTraversalException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service for browsing and retrieving documentation files.
 * Builds a file tree of .md files, caches the result, and validates paths.
 */
@Service
public class DocsService {

    private static final Logger log = LoggerFactory.getLogger(DocsService.class);

    private final DocsPathProperties docsPathProperties;
    private final DocsCacheManager docsCacheManager;

    public DocsService(DocsPathProperties docsPathProperties, DocsCacheManager docsCacheManager) {
        this.docsPathProperties = docsPathProperties;
        this.docsCacheManager = docsCacheManager;
    }

    /**
     * Returns the cached file tree, or builds it from the filesystem if not cached.
     * Returns an empty root node if docs path is not configured or directory doesn't exist.
     */
    public DocsTreeNode getFileTree() {
        return docsCacheManager.get().orElseGet(() -> {
            DocsTreeNode tree = buildTreeFromFilesystem();
            docsCacheManager.put(tree);
            return tree;
        });
    }

    /**
     * Retrieves the raw content of a Markdown file at the given relative path.
     *
     * @param relativePath the relative path to the .md file
     * @return the raw file content as a String
     * @throws DocsInvalidFileTypeException if the path does not end with .md
     * @throws DocsPathTraversalException   if the path resolves outside the docs root
     * @throws DocsFileNotFoundException    if the file does not exist
     */
    public String getFileContent(String relativePath) {
        if (!relativePath.endsWith(".md")) {
            throw new DocsInvalidFileTypeException(
                "Only Markdown (.md) files are served");
        }

        Path resolved = validatePath(relativePath);

        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            throw new DocsFileNotFoundException(
                "Documentation file not found: " + relativePath);
        }

        try {
            return Files.readString(resolved);
        } catch (IOException e) {
            throw new DocsFileNotFoundException(
                "Unable to read documentation file: " + relativePath);
        }
    }

    /**
     * Scheduled task that refreshes the cached file tree every 24 hours.
     * On failure, retains the previously cached tree and logs the error.
     */
    @Scheduled(fixedRate = 86400000)
    public void refreshCache() {
        try {
            DocsTreeNode tree = buildTreeFromFilesystem();
            docsCacheManager.put(tree);
            log.info("Documentation cache refreshed successfully");
        } catch (Exception e) {
            log.error("Failed to refresh documentation cache; retaining previous cached tree", e);
        }
    }

    /**
     * Validates that the given relative path resolves within the docs root directory.
     * Normalizes both the root and resolved path before comparing.
     *
     * @param relativePath the relative path to validate
     * @return the normalized, validated absolute path
     * @throws DocsPathTraversalException if the path resolves outside the docs root
     */
    public Path validatePath(String relativePath) {
        Path root = Path.of(docsPathProperties.path()).normalize();
        Path resolved = root.resolve(relativePath).normalize();

        if (!resolved.startsWith(root)) {
            throw new DocsPathTraversalException(
                "Access denied: path is outside the documentation directory");
        }

        return resolved;
    }

    /**
     * Builds the file tree from the filesystem.
     * Returns an empty root node if docs path is not configured or directory doesn't exist.
     */
    private DocsTreeNode buildTreeFromFilesystem() {
        String docsPath = docsPathProperties.path();

        if (docsPath == null || docsPath.isBlank()) {
            log.warn("Docs path is not configured; returning empty tree");
            return emptyRootNode();
        }

        Path root = Path.of(docsPath);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            log.warn("Docs directory does not exist: {}; returning empty tree", root);
            return emptyRootNode();
        }

        return buildTree(root, root);
    }

    /**
     * Recursively builds a DocsTreeNode tree from the given directory.
     * Includes only .md files and folders that contain .md files (directly or nested).
     * Package-private for property test access.
     *
     * @param current the current directory to scan
     * @param root    the docs root directory (for computing relative paths)
     * @return a DocsTreeNode representing the current directory
     */
    DocsTreeNode buildTree(Path current, Path root) {
        List<DocsTreeNode> children = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    DocsTreeNode subtree = buildTree(entry, root);
                    // Only include folders that contain .md files (directly or nested)
                    if (subtree.children() != null && !subtree.children().isEmpty()) {
                        children.add(subtree);
                    }
                } else if (Files.isRegularFile(entry) && entry.getFileName().toString().endsWith(".md")) {
                    String relativePath = root.relativize(entry).toString().replace('\\', '/');
                    children.add(new DocsTreeNode(
                        entry.getFileName().toString(),
                        "file",
                        relativePath,
                        null
                    ));
                }
            }
        } catch (IOException e) {
            log.error("Error reading directory: {}", current, e);
        }

        // Sort: folders first, then files, alphabetically within each group
        children.sort(Comparator
            .comparing((DocsTreeNode n) -> n.type().equals("file") ? 1 : 0)
            .thenComparing(DocsTreeNode::name));

        String name = root.equals(current) ? "" : current.getFileName().toString();
        String path = root.equals(current) ? "" : root.relativize(current).toString().replace('\\', '/');

        return new DocsTreeNode(name, "folder", path, children);
    }

    private static DocsTreeNode emptyRootNode() {
        return new DocsTreeNode("", "folder", "", List.of());
    }
}
