package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.dto.DriveItem;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Feature: my-drive-backend, Property 6: File upload round trip
class FileUploadPropertyTest {

    private static final String DRIVE_KEY = "sharedDrive";
    private static final String[] EXTENSIONS = {"txt", "pdf", "doc", "png", "jpg", "mp3", "xlsx"};

    private Path tempDir;

    /**
     * File upload round trip: for any valid file with random content and name,
     * uploading to root SHALL result in:
     * (a) the file existing on the filesystem with identical content
     * (b) the returned DriveItem having type "file"
     * (c) the returned DriveItem having fileType matching the extension
     * (d) the returned DriveItem having a correct human-readable size
     * (e) the returned DriveItem having an empty children array
     *
     * Validates: Requirements 4.1, 4.2
     */
    @Property(tries = 100)
    void fileUploadRoundTrip(
            @ForAll("randomFileContent") byte[] content,
            @ForAll("randomFileNames") String fileName
    ) throws IOException {
        tempDir = Files.createTempDirectory("file-upload-test-");

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

        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", fileName, "application/octet-stream", content
        );

        DriveItem result = service.uploadFile("1", DRIVE_KEY, multipartFile, "root", user);

        // (a) The file exists on the filesystem with identical content
        Path expectedPath = tempDir.resolve(fileName);
        assertThat(Files.exists(expectedPath)).isTrue();
        assertThat(Files.isRegularFile(expectedPath)).isTrue();
        byte[] savedContent = Files.readAllBytes(expectedPath);
        assertThat(savedContent).isEqualTo(content);

        // (b) The returned DriveItem has type "file"
        assertThat(result.type()).isEqualTo("file");

        // (c) The returned DriveItem has fileType matching the extension
        int dotIndex = fileName.lastIndexOf('.');
        String expectedExtension = (dotIndex >= 0 && dotIndex < fileName.length() - 1)
                ? fileName.substring(dotIndex + 1)
                : "";
        assertThat(result.fileType()).isEqualTo(expectedExtension);

        // (d) The returned DriveItem has a correct human-readable size
        String expectedSize = MyDriveService.formatFileSize(content.length);
        assertThat(result.size()).isEqualTo(expectedSize);

        // (e) The returned DriveItem has an empty children array
        assertThat(result.children()).isEmpty();
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
