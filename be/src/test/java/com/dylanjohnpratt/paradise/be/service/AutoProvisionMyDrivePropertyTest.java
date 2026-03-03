package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.dto.DriveItem;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Feature: my-drive-backend, Property 17: Auto-provision myDrive
class AutoProvisionMyDrivePropertyTest {

    private Path tempDir;

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

    /**
     * Auto-provision myDrive: for any userId where the per-user directory does not yet exist,
     * requesting myDrive contents SHALL:
     * (a) create the directory on the filesystem
     * (b) return a flat map containing exactly one entry — the root — with an empty children array
     *
     * Validates: Requirements 11.1, 11.2
     */
    @Property(tries = 100)
    void autoProvisionCreatesDirectoryAndReturnsEmptyRoot(
            @ForAll("numericUserIds") Long userId) throws IOException {

        tempDir = Files.createTempDirectory("autoprovision-test-");
        String username = "user" + userId;

        // Verify the per-user directory does not exist yet
        Path userDir = tempDir.resolve(username);
        assertThat(userDir).doesNotExist();

        // Configure DrivePathProperties with myDrive pointing to temp dir
        DrivePathProperties props = new DrivePathProperties(
                tempDir.toAbsolutePath().toString(),
                "/unused/sharedDrive",
                "/unused/adminDrive",
                "/unused/mediaCache",
                "/unused/plexUpload"
        );
        ItemMetadataRepository metadataRepo = mock(ItemMetadataRepository.class);
        when(metadataRepo.findByDriveKey(anyString())).thenReturn(List.of());
        MyDriveService service = new MyDriveService(metadataRepo, props, new DriveCacheManager(new com.dylanjohnpratt.paradise.be.config.DriveCacheProperties(null, false, false, false, false)));

        // Create user with id matching the userId
        User user = new User("user" + userId, "password", Set.of());
        user.setId(userId);

        // Call getDriveContents with driveKey="myDrive" using the username
        Map<String, DriveItem> flatMap = service.getDriveContents(username, "myDrive", user);

        // (a) The per-user directory now exists on the filesystem
        assertThat(userDir).exists().isDirectory();

        // (b) Flat map contains exactly one entry: root with empty children
        assertThat(flatMap).hasSize(1);
        assertThat(flatMap).containsKey("root");

        DriveItem root = flatMap.get("root");
        assertThat(root.id()).isEqualTo("root");
        assertThat(root.parentId()).isNull();
        assertThat(root.type()).isEqualTo("folder");
        assertThat(root.children()).isEmpty();

        // Clean up per-user directory for this iteration
        Files.deleteIfExists(userDir);
    }

    @Provide
    Arbitrary<Long> numericUserIds() {
        return Arbitraries.longs().between(1, 10000);
    }
}
