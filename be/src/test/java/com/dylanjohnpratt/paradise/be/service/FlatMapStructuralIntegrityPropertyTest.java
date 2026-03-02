package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.dto.DriveItem;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Feature: my-drive-backend, Property 1: Flat map structural integrity
class FlatMapStructuralIntegrityPropertyTest {

    private static final String DRIVE_KEY = "sharedDrive";
    private static final String[] EXTENSIONS = {"txt", "pdf", "jpg", "mp3", "docx", "png"};

    /**
     * Flat map structural integrity: for any random directory tree, the flat map SHALL satisfy:
     * (a) every file and folder appears as a keyed entry
     * (b) a "root" entry exists with parentId null
     * (c) every folder has type "folder" and children containing exactly its direct children IDs
     * (d) every file has type "file", fileType matching extension, non-null size, empty children
     * (e) every non-root entry's parentId references an existing folder entry
     *
     * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5
     */
    @Property(tries = 100)
    void flatMapStructuralIntegrity(@ForAll("randomTrees") TreeSpec treeSpec) throws IOException {
        Path tempDir = Files.createTempDirectory("flatmap-test-");
        try {
            // Build the random directory tree on disk
            createTree(tempDir, treeSpec);

            // Set up service with sharedDrive pointing to tempDir
            DrivePathProperties props = new DrivePathProperties(
                    "/unused/myDrive",
                    tempDir.toAbsolutePath().toString(),
                    "/unused/adminDrive",
                    "/unused/mediaCache",
                    "/unused/plexUpload"
            );
            ItemMetadataRepository metadataRepo = mock(ItemMetadataRepository.class);
            when(metadataRepo.findByDriveKey(anyString())).thenReturn(List.of());
            MyDriveService service = new MyDriveService(metadataRepo, props);

            User user = new User("testuser", "password", Set.of());
            user.setId(1L);

            Map<String, DriveItem> flatMap = service.getDriveContents("1", DRIVE_KEY, user);

            // Collect all filesystem paths for comparison
            Set<Path> allPaths = new HashSet<>();
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.forEach(allPaths::add);
            }

            // (a) Every file and folder in the tree appears as a keyed entry
            assertThat(flatMap).hasSize(allPaths.size());

            // (b) A "root" entry exists with parentId null
            assertThat(flatMap).containsKey("root");
            DriveItem root = flatMap.get("root");
            assertThat(root.parentId()).isNull();
            assertThat(root.type()).isEqualTo("folder");

            // Verify each item
            for (DriveItem item : flatMap.values()) {
                if ("folder".equals(item.type())) {
                    // (c) Folder: type is "folder", children contain exactly direct child IDs
                    assertThat(item.children()).isNotNull();

                    // Verify all children IDs reference existing entries
                    for (String childId : item.children()) {
                        assertThat(flatMap).containsKey(childId);
                        // Each child's parentId should point back to this folder
                        assertThat(flatMap.get(childId).parentId()).isEqualTo(item.id());
                    }
                } else {
                    // (d) File: type is "file", fileType matches extension, non-null size, empty children
                    assertThat(item.type()).isEqualTo("file");
                    assertThat(item.children()).isEmpty();
                    assertThat(item.size()).isNotNull();

                    // fileType should match the file extension
                    String name = item.name();
                    int dotIndex = name.lastIndexOf('.');
                    if (dotIndex >= 0 && dotIndex < name.length() - 1) {
                        String expectedExt = name.substring(dotIndex + 1);
                        assertThat(item.fileType()).isEqualTo(expectedExt);
                    }
                }

                // (e) Every non-root entry's parentId references an existing folder entry
                if (item.parentId() != null) {
                    assertThat(flatMap).containsKey(item.parentId());
                    assertThat(flatMap.get(item.parentId()).type()).isEqualTo("folder");
                }
            }

            // Verify children completeness: each folder's children list has exactly the right count
            Map<String, List<String>> expectedChildren = new HashMap<>();
            for (DriveItem item : flatMap.values()) {
                if (item.parentId() != null) {
                    expectedChildren.computeIfAbsent(item.parentId(), k -> new ArrayList<>()).add(item.id());
                }
            }
            for (DriveItem item : flatMap.values()) {
                if ("folder".equals(item.type())) {
                    List<String> expected = expectedChildren.getOrDefault(item.id(), List.of());
                    assertThat(item.children()).containsExactlyInAnyOrderElementsOf(expected);
                }
            }

        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Provide
    Arbitrary<TreeSpec> randomTrees() {
        Arbitrary<Integer> folderCount = Arbitraries.integers().between(1, 5);
        Arbitrary<Integer> fileCount = Arbitraries.integers().between(1, 5);
        Arbitrary<Integer> maxDepth = Arbitraries.integers().between(1, 3);

        return Combinators.combine(folderCount, fileCount, maxDepth)
                .as((f, fi, d) -> new TreeSpec(f, fi, d));
    }

    record TreeSpec(int folderCount, int fileCount, int maxDepth) {}

    private void createTree(Path root, TreeSpec spec) throws IOException {
        Random rng = new Random();
        List<Path> folders = new ArrayList<>();
        folders.add(root);

        // Create random subdirectories at various depths
        for (int i = 0; i < spec.folderCount(); i++) {
            Path parent = folders.get(rng.nextInt(folders.size()));
            int currentDepth = root.relativize(parent).getNameCount();
            if (currentDepth >= spec.maxDepth()) {
                parent = root;
            }
            String folderName = "dir" + i + randomAlphanumeric(rng, 4);
            Path newFolder = parent.resolve(folderName);
            Files.createDirectories(newFolder);
            folders.add(newFolder);
        }

        // Create random files in random folders
        for (int i = 0; i < spec.fileCount(); i++) {
            Path parent = folders.get(rng.nextInt(folders.size()));
            String ext = EXTENSIONS[rng.nextInt(EXTENSIONS.length)];
            String fileName = "file" + i + randomAlphanumeric(rng, 4) + "." + ext;
            Path newFile = parent.resolve(fileName);
            Files.writeString(newFile, "content" + i);
        }
    }

    private String randomAlphanumeric(Random rng, int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
