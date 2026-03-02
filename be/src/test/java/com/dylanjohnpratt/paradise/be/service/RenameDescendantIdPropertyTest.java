package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.dto.DriveItem;
import com.dylanjohnpratt.paradise.be.dto.UpdateItemRequest;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Feature: my-drive-backend, Property 11: Rename with descendant ID update
@SuppressWarnings("null")
class RenameDescendantIdPropertyTest {

    private static final String DRIVE_KEY = "sharedDrive";

    private Path tempDir;

    private MyDriveService createService() {
        DrivePathProperties props = new DrivePathProperties(
                "/unused/myDrive",
                tempDir.toAbsolutePath().toString(),
                "/unused/adminDrive",
                "/unused/mediaCache",
                "/unused/plexUpload"
        );
        ItemMetadataRepository metadataRepo = mock(ItemMetadataRepository.class);
        when(metadataRepo.findByDriveKey(anyString())).thenReturn(List.of());
        when(metadataRepo.findById(any(String.class))).thenReturn(Optional.empty());
        when(metadataRepo.findAllById(anyCollection())).thenReturn(List.of());
        return new MyDriveService(metadataRepo, props);
    }

    private User createUser() {
        User user = new User("testuser", "password", Set.of());
        user.setId(1L);
        return user;
    }

    /**
     * Property 11: Rename with descendant ID update
     *
     * For any folder containing a child file, renaming the folder SHALL result in:
     * (a) The old filesystem path no longer existing
     * (b) The new filesystem path existing
     * (c) The child file existing under the new path
     * (d) The returned DriveItem having the new name
     * (e) The returned DriveItem having a recomputed ID matching the new path
     *
     * Validates: Requirements 6.1, 6.3
     */
    @Property(tries = 100)
    void renameWithDescendantIdUpdate(
            @ForAll("validFolderNames") String folderName,
            @ForAll("validFolderNames") String newFolderName,
            @ForAll("validFileNames") String childFileName
    ) throws IOException {
        // Skip when old and new names match (case-insensitive for Windows filesystem)
        Assume.that(!folderName.equalsIgnoreCase(newFolderName));

        tempDir = Files.createTempDirectory("rename-descendant-test-");

        // 1. Create a folder with a child file inside it
        Path folderPath = tempDir.resolve(folderName);
        Files.createDirectory(folderPath);
        Path childFilePath = folderPath.resolve(childFileName);
        Files.write(childFilePath, "test content".getBytes());

        // 2. Compute the folder's itemId
        String folderItemId = MyDriveService.generateItemId(DRIVE_KEY, folderName);

        // 3. Create service and call updateItem to rename the folder
        MyDriveService service = createService();
        User user = createUser();
        UpdateItemRequest request = new UpdateItemRequest(newFolderName, null);

        DriveItem result = service.updateItem("1", DRIVE_KEY, folderItemId, request, user);

        // (a) The old filesystem path no longer exists
        Path oldPath = tempDir.resolve(folderName);
        assertThat(Files.exists(oldPath))
                .as("Old path should no longer exist after rename")
                .isFalse();

        // (b) The new filesystem path exists
        Path newPath = tempDir.resolve(newFolderName);
        assertThat(Files.exists(newPath))
                .as("New path should exist after rename")
                .isTrue();
        assertThat(Files.isDirectory(newPath))
                .as("New path should be a directory")
                .isTrue();

        // (c) The child file exists under the new path
        Path newChildPath = newPath.resolve(childFileName);
        assertThat(Files.exists(newChildPath))
                .as("Child file should exist under the new path")
                .isTrue();

        // (d) The returned DriveItem has the new name
        assertThat(result.name()).isEqualTo(newFolderName);

        // (e) The returned DriveItem has a recomputed ID matching the new path
        String expectedNewId = MyDriveService.generateItemId(DRIVE_KEY, newFolderName);
        assertThat(result.id()).isEqualTo(expectedNewId);

        // Also verify the child's ID would be recomputed correctly
        String expectedChildId = MyDriveService.generateItemId(DRIVE_KEY, newFolderName + "/" + childFileName);
        assertThat(result.children())
                .as("Children should contain the recomputed child ID")
                .contains(expectedChildId);
    }

    @Provide
    Arbitrary<String> validFolderNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(15);
    }

    @Provide
    Arbitrary<String> validFileNames() {
        Arbitrary<String> baseName = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(10);
        Arbitrary<String> extension = Arbitraries.of("txt", "pdf", "doc", "png", "jpg");
        return Combinators.combine(baseName, extension).as((name, ext) -> name + "." + ext);
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
