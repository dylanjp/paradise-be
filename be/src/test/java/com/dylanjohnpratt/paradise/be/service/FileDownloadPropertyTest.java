package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
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

// Feature: my-drive-backend, Property 7: File download round trip
class FileDownloadPropertyTest {

    private static final String DRIVE_KEY = "sharedDrive";
    private static final String[] EXTENSIONS = {"txt", "pdf", "doc", "png", "jpg", "mp3", "xlsx"};

    private Path tempDir;

    /**
     * File download round trip: for any file that exists on the filesystem,
     * downloading it SHALL return:
     * (a) a Path pointing to the correct file whose content is identical to the original
     * (b) the filename from the returned Path matches the original filename
     *     (used by the controller to set Content-Disposition: attachment; filename="<original_filename>")
     *
     * Validates: Requirements 5.1, 5.3
     */
    @Property(tries = 100)
    void fileDownloadRoundTrip(
            @ForAll("randomFileContent") byte[] content,
            @ForAll("randomFileNames") String fileName
    ) throws IOException {
        tempDir = Files.createTempDirectory("file-download-test-");

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

        // Create a file with random content in the temp directory
        Path filePath = tempDir.resolve(fileName);
        Files.write(filePath, content);

        // Compute the item ID the same way the service does
        String itemId = MyDriveService.generateItemId(DRIVE_KEY, fileName);

        // Download the file via the service
        Path downloadedPath = service.downloadFile("1", DRIVE_KEY, itemId, user);

        // (a) The returned Path points to the correct file and content matches
        assertThat(downloadedPath).isNotNull();
        assertThat(Files.exists(downloadedPath)).isTrue();
        assertThat(Files.isRegularFile(downloadedPath)).isTrue();
        byte[] downloadedContent = Files.readAllBytes(downloadedPath);
        assertThat(downloadedContent).isEqualTo(content);

        // (b) The filename from the returned Path matches the original filename
        //     (controller uses this for Content-Disposition header)
        assertThat(downloadedPath.getFileName().toString()).isEqualTo(fileName);
    }

    @Provide
    Arbitrary<byte[]> randomFileContent() {
        return Arbitraries.bytes().array(byte[].class)
                .ofMinSize(1)
                .ofMaxSize(1000);
    }

    @Provide
    Arbitrary<String> randomFileNames() {
        Arbitrary<String> baseName = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(15);
        Arbitrary<String> extension = Arbitraries.of(EXTENSIONS);
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
