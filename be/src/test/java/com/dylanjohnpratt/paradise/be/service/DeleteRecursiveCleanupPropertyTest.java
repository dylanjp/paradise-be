package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// Feature: my-drive-backend, Property 14: Delete with recursive cleanup
class DeleteRecursiveCleanupPropertyTest {

    private static final String DRIVE_KEY = "sharedDrive";

    private Path tempDir;

    private MyDriveService createService(ItemMetadataRepository metadataRepo) {
        DrivePathProperties props = new DrivePathProperties(
                "/unused/myDrive",
                tempDir.toAbsolutePath().toString(),
                "/unused/adminDrive",
                "/unused/mediaCache",
                "/unused/plexUpload"
        );
        return new MyDriveService(metadataRepo, props, new DriveCacheManager(new com.dylanjohnpratt.paradise.be.config.DriveCacheProperties(null, false, false, false, false)));
    }

    private User createUser() {
        User user = new User("testuser", "password", Set.of());
        user.setId(1L);
        return user;
    }

    /**
     * Delete with recursive cleanup — deleting a folder:
     * (a) The folder no longer exists on the filesystem
     * (b) All descendant files and folders are also removed
     * (c) The metadata records for the item and descendants were deleted
     *     (verify deleteByItemIdIn was called with the correct IDs)
     *
     * Validates: Requirements 7.1, 7.2, 7.3, 9.3
     */
    @Property(tries = 100)
    @SuppressWarnings("unchecked")
    void deleteFolderRecursivelyCleansFsAndMetadata(
            @ForAll("validFolderNames") String folderName,
            @ForAll("validFileNames") String childFileName,
            @ForAll("validFolderNames") String subFolderName,
            @ForAll("validFileNames") String nestedFileName
    ) throws IOException {
        tempDir = Files.createTempDirectory("delete-recursive-test-");

        // Build a directory tree: folder/childFile, folder/subFolder/nestedFile
        Path folder = tempDir.resolve(folderName);
        Files.createDirectory(folder);
        Files.write(folder.resolve(childFileName), "child-content".getBytes());
        Path subFolder = folder.resolve(subFolderName);
        Files.createDirectory(subFolder);
        Files.write(subFolder.resolve(nestedFileName), "nested-content".getBytes());

        // Compute expected IDs for all items in the tree
        String folderId = MyDriveService.generateItemId(DRIVE_KEY, folderName);
        String childFileId = MyDriveService.generateItemId(DRIVE_KEY, folderName + "/" + childFileName);
        String subFolderId = MyDriveService.generateItemId(DRIVE_KEY, folderName + "/" + subFolderName);
        String nestedFileId = MyDriveService.generateItemId(DRIVE_KEY, folderName + "/" + subFolderName + "/" + nestedFileName);
        Set<String> expectedIds = Set.of(folderId, childFileId, subFolderId, nestedFileId);

        // Mock the repository
        ItemMetadataRepository metadataRepo = mock(ItemMetadataRepository.class);
        when(metadataRepo.findByDriveKey(anyString())).thenReturn(List.of());

        MyDriveService service = createService(metadataRepo);
        User user = createUser();

        // Delete the folder
        service.deleteItem("1", DRIVE_KEY, folderId, user);

        // (a) The folder no longer exists on the filesystem
        assertThat(Files.exists(folder)).isFalse();

        // (b) All descendant files and folders are also removed
        assertThat(Files.exists(folder.resolve(childFileName))).isFalse();
        assertThat(Files.exists(subFolder)).isFalse();
        assertThat(Files.exists(subFolder.resolve(nestedFileName))).isFalse();

        // (c) Verify deleteByItemIdIn was called with the correct IDs
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(metadataRepo).deleteByItemIdIn(captor.capture());
        Collection<String> deletedIds = captor.getValue();
        assertThat(new HashSet<>(deletedIds)).isEqualTo(expectedIds);
    }

    /**
     * Delete with recursive cleanup — deleting a single file:
     * (a) The file no longer exists on the filesystem
     * (b) The metadata record for the file was deleted
     *
     * Validates: Requirements 7.1, 9.3
     */
    @Property(tries = 100)
    @SuppressWarnings("unchecked")
    void deleteSingleFileCleansFsAndMetadata(
            @ForAll("validFileNames") String fileName
    ) throws IOException {
        tempDir = Files.createTempDirectory("delete-file-test-");

        // Create a single file in the drive root
        Files.write(tempDir.resolve(fileName), "file-content".getBytes());

        String fileId = MyDriveService.generateItemId(DRIVE_KEY, fileName);

        ItemMetadataRepository metadataRepo = mock(ItemMetadataRepository.class);
        when(metadataRepo.findByDriveKey(anyString())).thenReturn(List.of());

        MyDriveService service = createService(metadataRepo);
        User user = createUser();

        service.deleteItem("1", DRIVE_KEY, fileId, user);

        // (a) The file no longer exists
        assertThat(Files.exists(tempDir.resolve(fileName))).isFalse();

        // (b) Verify deleteByItemIdIn was called with the file's ID
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(metadataRepo).deleteByItemIdIn(captor.capture());
        Collection<String> deletedIds = captor.getValue();
        assertThat(deletedIds).containsExactly(fileId);
    }

    @Provide
    Arbitrary<String> validFolderNames() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(15);
    }

    @Provide
    Arbitrary<String> validFileNames() {
        Arbitrary<String> baseName = Arbitraries.strings()
                .alpha()
                .numeric()
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
