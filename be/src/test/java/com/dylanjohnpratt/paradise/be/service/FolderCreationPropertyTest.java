package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.dto.CreateFolderRequest;
import com.dylanjohnpratt.paradise.be.dto.DriveItem;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Feature: my-drive-backend, Property 5: Folder creation round trip
class FolderCreationPropertyTest {

    private static final String DRIVE_KEY = "sharedDrive";

    private Path tempDir;

    /**
     * Folder creation round trip: for any valid folder name, creating a folder with parentId="root"
     * SHALL result in:
     * (a) the directory existing on the filesystem inside the parent
     * (b) the returned DriveItem having type "folder"
     * (c) the returned DriveItem having an empty children array
     * (d) the returned DriveItem having a deterministic ID (verified independently)
     * (e) the returned DriveItem having parentId matching the request's parentId
     *
     * Validates: Requirements 3.1, 3.2
     */
    @Property(tries = 100)
    void folderCreationRoundTrip(@ForAll("validFolderNames") String folderName) throws IOException {
        tempDir = Files.createTempDirectory("folder-creation-test-");

        DrivePathProperties props = new DrivePathProperties(
                "/unused/myDrive",
                tempDir.toAbsolutePath().toString(),
                "/unused/adminDrive",
                "/unused/mediaCache",
                "/unused/plexUpload"
        );
        ItemMetadataRepository metadataRepo = mock(ItemMetadataRepository.class);
        when(metadataRepo.findByDriveKey(anyString())).thenReturn(List.of());
        MyDriveService service = new MyDriveService(metadataRepo, props, new DriveCacheManager(new com.dylanjohnpratt.paradise.be.config.DriveCacheProperties(null, false, false, false, false)));

        User user = new User("testuser", "password", Set.of());
        user.setId(1L);

        CreateFolderRequest request = new CreateFolderRequest(folderName, "root");
        DriveItem result = service.createFolder("1", DRIVE_KEY, request, user);

        // (a) The directory exists on the filesystem inside the parent
        Path expectedPath = tempDir.resolve(folderName);
        assertThat(Files.exists(expectedPath)).isTrue();
        assertThat(Files.isDirectory(expectedPath)).isTrue();

        // (b) The returned DriveItem has type "folder"
        assertThat(result.type()).isEqualTo("folder");

        // (c) The returned DriveItem has an empty children array
        assertThat(result.children()).isEmpty();

        // (d) The returned DriveItem has a deterministic ID (verify independently)
        String expectedId = MyDriveService.generateItemId(DRIVE_KEY, folderName);
        assertThat(result.id()).isEqualTo(expectedId);

        // (e) The returned DriveItem has parentId matching the request's parentId
        assertThat(result.parentId()).isEqualTo("root");
    }

    @Provide
    Arbitrary<String> validFolderNames() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(20);
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
