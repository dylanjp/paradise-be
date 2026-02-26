package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.exception.DriveItemConflictException;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// Feature: my-drive-backend, Property 19: Plex duplicate returns 409
class PlexDuplicateConflictPropertyTest {

    private static final String[] EXTENSIONS = {"txt", "pdf", "mp4", "mkv", "mp3", "flac", "jpg", "png"};

    private Path tempDir;

    /**
     * Plex duplicate returns 409: for any file name that already exists in the
     * PLEX_UPLOAD_PATH directory, uploading a file with the same name SHALL
     * return HTTP 409 Conflict (DriveItemConflictException).
     *
     * Validates: Requirements 12.5
     */
    @Property(tries = 100)
    void plexDuplicateReturnsConflict(
            @ForAll("randomFileContent") byte[] existingContent,
            @ForAll("randomFileContent") byte[] newContent,
            @ForAll("randomFileNames") String fileName
    ) throws IOException {
        tempDir = Files.createTempDirectory("plex-duplicate-test-");

        DrivePathProperties props = new DrivePathProperties(
                "/unused/myDrive",
                "/unused/sharedDrive",
                "/unused/adminDrive",
                "/unused/mediaCache",
                tempDir.toAbsolutePath().toString()
        );
        ItemMetadataRepository metadataRepo = mock(ItemMetadataRepository.class);
        MyDriveService service = new MyDriveService(metadataRepo, props);

        User user = new User("testuser", "password", Set.of());
        user.setId(1L);

        // Pre-create a file with the same name in the Plex upload folder
        Path existingFile = tempDir.resolve(fileName);
        Files.write(existingFile, existingContent);

        // Attempt to upload a file with the same name
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", fileName, "application/octet-stream", newContent
        );

        assertThatThrownBy(() -> service.uploadToPlex("1", multipartFile, user))
                .isInstanceOf(DriveItemConflictException.class);
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
