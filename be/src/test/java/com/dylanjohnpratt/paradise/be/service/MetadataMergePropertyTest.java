package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.dto.DriveItem;
import com.dylanjohnpratt.paradise.be.model.ItemMetadata;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Feature: my-drive-backend, Property 2: Metadata merge into flat map
class MetadataMergePropertyTest {

    private static final String DRIVE_KEY = "sharedDrive";
    private static final String[] COLORS = {"#FF0000", "#00FF00", "#0000FF", "blue", "red", "green", "#ABCDEF", "#123456"};
    private static final String[] EXTENSIONS = {"txt", "pdf", "jpg", "mp3"};

    /**
     * Metadata merge into flat map: for any set of ItemMetadata records and any directory tree,
     * building the flat map SHALL produce DriveItem entries where the color field equals the
     * stored metadata color for items that have metadata, and is null for items without metadata.
     *
     * Validates: Requirements 1.6, 9.4
     */
    @Property(tries = 100)
    void metadataMergeIntoFlatMap(@ForAll("testScenarios") TestScenario scenario) throws IOException {
        Path tempDir = Files.createTempDirectory("metadata-merge-test-");
        try {
            // Build the random directory tree on disk
            List<String> relativePaths = createTree(tempDir, scenario.treeSpec());

            // Compute item IDs for some items and assign random colors
            Map<String, String> expectedColors = new HashMap<>();
            List<ItemMetadata> metadataRecords = new ArrayList<>();

            for (int i = 0; i < relativePaths.size() && i < scenario.colorAssignments().size(); i++) {
                ColorAssignment assignment = scenario.colorAssignments().get(i);
                if (assignment.hasColor()) {
                    String relativePath = relativePaths.get(i);
                    String itemId = MyDriveService.generateItemId(DRIVE_KEY, relativePath);
                    String color = assignment.color();
                    expectedColors.put(itemId, color);
                    metadataRecords.add(new ItemMetadata(itemId, DRIVE_KEY, color));
                }
            }

            // Set up service with sharedDrive pointing to tempDir
            DrivePathProperties props = new DrivePathProperties(
                    "/unused/myDrive",
                    tempDir.toAbsolutePath().toString(),
                    "/unused/adminDrive",
                    "/unused/mediaCache",
                    "/unused/plexUpload"
            );
            ItemMetadataRepository metadataRepo = mock(ItemMetadataRepository.class);
            when(metadataRepo.findByDriveKey(anyString())).thenReturn(metadataRecords);
            MyDriveService service = new MyDriveService(metadataRepo, props);

            User user = new User("testuser", "password", Set.of());
            user.setId(1L);

            Map<String, DriveItem> flatMap = service.getDriveContents("1", DRIVE_KEY, user);

            // Verify: items with metadata have the correct color, items without have null
            for (Map.Entry<String, DriveItem> entry : flatMap.entrySet()) {
                String itemId = entry.getKey();
                DriveItem item = entry.getValue();

                if (expectedColors.containsKey(itemId)) {
                    assertThat(item.color())
                            .as("Item '%s' should have color '%s' from metadata", item.name(), expectedColors.get(itemId))
                            .isEqualTo(expectedColors.get(itemId));
                } else {
                    assertThat(item.color())
                            .as("Item '%s' should have null color (no metadata)", item.name())
                            .isNull();
                }
            }

            // Also verify all metadata records were applied (no metadata was lost)
            for (String metadataItemId : expectedColors.keySet()) {
                if (flatMap.containsKey(metadataItemId)) {
                    assertThat(flatMap.get(metadataItemId).color()).isEqualTo(expectedColors.get(metadataItemId));
                }
            }

        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Provide
    Arbitrary<TestScenario> testScenarios() {
        Arbitrary<TreeSpec> trees = Combinators.combine(
                Arbitraries.integers().between(1, 4),
                Arbitraries.integers().between(1, 4),
                Arbitraries.integers().between(1, 3)
        ).as((f, fi, d) -> new TreeSpec(f, fi, d));

        Arbitrary<List<ColorAssignment>> colors = colorAssignments().list().ofMinSize(1).ofMaxSize(8);

        return Combinators.combine(trees, colors).as(TestScenario::new);
    }

    private Arbitrary<ColorAssignment> colorAssignments() {
        Arbitrary<Boolean> hasColor = Arbitraries.of(true, false);
        Arbitrary<String> color = Arbitraries.of(COLORS);
        return Combinators.combine(hasColor, color).as((h, c) -> new ColorAssignment(h, c));
    }

    record TestScenario(TreeSpec treeSpec, List<ColorAssignment> colorAssignments) {}
    record TreeSpec(int folderCount, int fileCount, int maxDepth) {}
    record ColorAssignment(boolean hasColor, String color) {}

    /**
     * Creates a random directory tree and returns the relative paths of all non-root items.
     */
    private List<String> createTree(Path root, TreeSpec spec) throws IOException {
        Random rng = new Random();
        List<Path> folders = new ArrayList<>();
        folders.add(root);
        List<String> relativePaths = new ArrayList<>();

        // Create random subdirectories
        for (int i = 0; i < spec.folderCount(); i++) {
            Path parent = folders.get(rng.nextInt(folders.size()));
            int currentDepth = root.relativize(parent).getNameCount();
            if (currentDepth >= spec.maxDepth()) {
                parent = root;
            }
            String folderName = "dir" + i + randomAlpha(rng, 4);
            Path newFolder = parent.resolve(folderName);
            Files.createDirectories(newFolder);
            folders.add(newFolder);
            relativePaths.add(root.relativize(newFolder).toString().replace('\\', '/'));
        }

        // Create random files
        for (int i = 0; i < spec.fileCount(); i++) {
            Path parent = folders.get(rng.nextInt(folders.size()));
            String ext = EXTENSIONS[rng.nextInt(EXTENSIONS.length)];
            String fileName = "file" + i + randomAlpha(rng, 4) + "." + ext;
            Path newFile = parent.resolve(fileName);
            Files.writeString(newFile, "content" + i);
            relativePaths.add(root.relativize(newFile).toString().replace('\\', '/'));
        }

        return relativePaths;
    }

    private String randomAlpha(Random rng, int length) {
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
