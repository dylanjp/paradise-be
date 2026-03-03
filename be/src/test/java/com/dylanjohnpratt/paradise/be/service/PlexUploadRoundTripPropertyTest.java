package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.dto.PlexUploadResponse;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// Feature: my-drive-backend, Property 18: Plex upload round trip
class PlexUploadRoundTripPropertyTest {

    private static final String[] EXTENSIONS = {"txt", "pdf", "mp4", "mkv", "mp3", "flac", "jpg", "png"};

    private Path tempDir;

    /**
     * Plex upload round trip: for any file with arbitrary content and name,
     * uploading to the Plex endpoint SHALL result in:
     * (a) the file existing in the PLEX_UPLOAD_PATH directory with identical content
     * (b) the response containing the correct file name
     * (c) the response containing the correct human-readable size
     *
     * Validates: Requirements 12.2, 12.4
     */
    @Property(tries = 100)
    void plexUploadRoundTrip(
            @ForAll("randomFileContent") byte[] content,
            @ForAll("randomFileNames") String fileName
    ) throws IOException {
        tempDir = Files.createTempDirectory("plex-upload-test-");

        DrivePathProperties props = new DrivePathProperties(
                "/unused/myDrive",
                "/unused/sharedDrive",
                "/unused/adminDrive",
                "/unused/mediaCache",
                tempDir.toAbsolutePath().toString()
        );
        ItemMetadataRepository metadataRepo = mock(ItemMetadataRepository.class);
        MyDriveService service = new MyDriveService(metadataRepo, props, new DriveCacheManager(new com.dylanjohnpratt.paradise.be.config.DriveCacheProperties(null, false, false, false, false)));

        User user = new User("testuser", "password", Set.of());
        user.setId(1L);

        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", fileName, "application/octet-stream", content
        );

        PlexUploadResponse result = service.uploadToPlex("1", multipartFile, user);

        // (a) The file exists in the Plex upload directory with identical content
        Path expectedPath = tempDir.resolve(fileName);
        assertThat(Files.exists(expectedPath)).isTrue();
        assertThat(Files.isRegularFile(expectedPath)).isTrue();
        byte[] savedContent = Files.readAllBytes(expectedPath);
        assertThat(savedContent).isEqualTo(content);

        // (b) The response contains the correct file name
        assertThat(result.fileName()).isEqualTo(fileName);

        // (c) The response contains the correct human-readable size
        String expectedSize = MyDriveService.formatFileSize(content.length);
        assertThat(result.size()).isEqualTo(expectedSize);
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
