package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.dto.DriveItem;
import com.dylanjohnpratt.paradise.be.dto.UpdateItemRequest;
import com.dylanjohnpratt.paradise.be.model.ItemMetadata;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Feature: my-drive-backend, Property 12: Color persistence round trip
@SuppressWarnings("null")
class ColorPersistencePropertyTest {

    private static final String DRIVE_KEY = "sharedDrive";

    private Path tempDir;

    /**
     * Property 12: Color persistence round trip
     *
     * For any item and any color value, updating the item's color and then querying
     * the metadata SHALL return the same color value.
     *
     * Validates: Requirements 6.2, 9.2
     */
    @Property(tries = 100)
    void colorPersistenceRoundTrip(
            @ForAll("validFolderNames") String folderName,
            @ForAll("colorValues") String color
    ) throws IOException {
        tempDir = Files.createTempDirectory("color-persistence-test-");

        // 1. Create a folder in the temp dir
        Path folderPath = tempDir.resolve(folderName);
        Files.createDirectory(folderPath);

        // 2. Compute its itemId
        String itemId = MyDriveService.generateItemId(DRIVE_KEY, folderName);

        // 3. Set up an in-memory map to simulate repository behavior
        Map<String, ItemMetadata> metadataStore = new HashMap<>();

        ItemMetadataRepository metadataRepo = mock(ItemMetadataRepository.class);
        when(metadataRepo.findByDriveKey(anyString())).thenReturn(List.of());
        when(metadataRepo.findById(any(String.class))).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return Optional.ofNullable(metadataStore.get(id));
        });
        when(metadataRepo.save(any(ItemMetadata.class))).thenAnswer(invocation -> {
            ItemMetadata saved = invocation.getArgument(0);
            metadataStore.put(saved.getItemId(), saved);
            return saved;
        });
        when(metadataRepo.findAllById(anyList())).thenReturn(List.of());

        DrivePathProperties props = new DrivePathProperties(
                "/unused/myDrive",
                tempDir.toAbsolutePath().toString(),
                "/unused/adminDrive",
                "/unused/mediaCache",
                "/unused/plexUpload"
        );
        MyDriveService service = new MyDriveService(metadataRepo, props);

        User user = new User("testuser", "password", Set.of());
        user.setId(1L);

        // 4. Call updateItem with the color (name=null)
        UpdateItemRequest request = new UpdateItemRequest(null, color);
        DriveItem result = service.updateItem("1", DRIVE_KEY, itemId, request, user);

        // 5a. Verify the returned DriveItem has the correct color
        assertThat(result.color())
                .as("Returned DriveItem should have the color that was set")
                .isEqualTo(color);

        // 5b. Verify the metadata store contains the correct color
        assertThat(metadataStore).containsKey(itemId);
        assertThat(metadataStore.get(itemId).getColor())
                .as("Persisted metadata should have the same color")
                .isEqualTo(color);
        assertThat(metadataStore.get(itemId).getDriveKey())
                .as("Persisted metadata should have the correct driveKey")
                .isEqualTo(DRIVE_KEY);
    }

    @Provide
    Arbitrary<String> validFolderNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(15);
    }

    @Provide
    Arbitrary<String> colorValues() {
        Arbitrary<String> hexColors = Arbitraries.strings()
                .withCharRange('0', '9')
                .withCharRange('A', 'F')
                .withCharRange('a', 'f')
                .ofLength(6)
                .map(hex -> "#" + hex);
        Arbitrary<String> namedColors = Arbitraries.of(
                "red", "blue", "green", "yellow", "purple", "orange", "pink", "cyan"
        );
        return Arbitraries.oneOf(hexColors, namedColors);
    }

    @AfterProperty
    void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
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
}
