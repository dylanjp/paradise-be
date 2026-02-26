package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.dto.CreateFolderRequest;
import com.dylanjohnpratt.paradise.be.exception.DownloadFolderException;
import com.dylanjohnpratt.paradise.be.exception.DriveItemConflictException;
import com.dylanjohnpratt.paradise.be.exception.DriveItemNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Feature: my-drive-backend, Properties 8, 9, 10, 13: Error conditions
class ErrorConditionsPropertyTest {

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
        return new MyDriveService(metadataRepo, props);
    }

    private User createUser() {
        User user = new User("testuser", "password", Set.of());
        user.setId(1L);
        return user;
    }

    // -----------------------------------------------------------------------
    // Property 8: Non-existent parent returns 404
    // For any folder creation or file upload request where the parentId does
    // not correspond to an existing folder, the service SHALL throw
    // DriveItemNotFoundException.
    //
    // Validates: Requirements 3.3, 4.4
    // -----------------------------------------------------------------------

    @Property(tries = 100)
    void nonExistentParentCreateFolderReturns404(
            @ForAll("nonExistentParentIds") String parentId
    ) throws IOException {
        tempDir = Files.createTempDirectory("error-nonexistent-parent-");
        MyDriveService service = createService();
        User user = createUser();

        CreateFolderRequest request = new CreateFolderRequest("newFolder", parentId);

        assertThatThrownBy(() -> service.createFolder("1", DRIVE_KEY, request, user))
                .isInstanceOf(DriveItemNotFoundException.class);
    }

    @Property(tries = 100)
    void nonExistentParentUploadFileReturns404(
            @ForAll("nonExistentParentIds") String parentId
    ) throws IOException {
        tempDir = Files.createTempDirectory("error-nonexistent-parent-upload-");
        MyDriveService service = createService();
        User user = createUser();

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "content".getBytes()
        );

        assertThatThrownBy(() -> service.uploadFile("1", DRIVE_KEY, file, parentId, user))
                .isInstanceOf(DriveItemNotFoundException.class);
    }

    @Provide
    Arbitrary<String> nonExistentParentIds() {
        // Generate random strings that won't match any real item ID or "root"
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(30)
                .filter(s -> !"root".equals(s));
    }

    // -----------------------------------------------------------------------
    // Property 9: Duplicate name returns 409
    // For any folder creation or file upload where the target name already
    // exists in the same parent directory, the service SHALL throw
    // DriveItemConflictException.
    //
    // Validates: Requirements 3.4, 4.5, 6.5
    // -----------------------------------------------------------------------

    @Property(tries = 100)
    void duplicateFolderNameReturns409(
            @ForAll("validItemNames") String folderName
    ) throws IOException {
        tempDir = Files.createTempDirectory("error-duplicate-folder-");
        MyDriveService service = createService();
        User user = createUser();

        // Pre-create a folder with the same name in root
        Files.createDirectory(tempDir.resolve(folderName));

        CreateFolderRequest request = new CreateFolderRequest(folderName, "root");

        assertThatThrownBy(() -> service.createFolder("1", DRIVE_KEY, request, user))
                .isInstanceOf(DriveItemConflictException.class);
    }

    @Property(tries = 100)
    void duplicateFileNameReturns409(
            @ForAll("validFileNames") String fileName
    ) throws IOException {
        tempDir = Files.createTempDirectory("error-duplicate-file-");
        MyDriveService service = createService();
        User user = createUser();

        // Pre-create a file with the same name in root
        Files.write(tempDir.resolve(fileName), "existing".getBytes());

        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, "application/octet-stream", "new content".getBytes()
        );

        assertThatThrownBy(() -> service.uploadFile("1", DRIVE_KEY, file, "root", user))
                .isInstanceOf(DriveItemConflictException.class);
    }

    @Provide
    Arbitrary<String> validItemNames() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> validFileNames() {
        Arbitrary<String> baseName = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(15);
        Arbitrary<String> extension = Arbitraries.of("txt", "pdf", "doc", "png", "jpg");
        return Combinators.combine(baseName, extension).as((name, ext) -> name + "." + ext);
    }

    // -----------------------------------------------------------------------
    // Property 10: Download folder returns 400
    // For any item ID that corresponds to a folder (not a file), a download
    // request SHALL throw DownloadFolderException.
    //
    // Validates: Requirements 5.5
    // -----------------------------------------------------------------------

    @Property(tries = 100)
    void downloadFolderReturns400(
            @ForAll("validItemNames") String folderName
    ) throws IOException {
        tempDir = Files.createTempDirectory("error-download-folder-");
        MyDriveService service = createService();
        User user = createUser();

        // Create a folder in the temp dir
        Files.createDirectory(tempDir.resolve(folderName));

        // Compute its itemId the same way the service does
        String itemId = MyDriveService.generateItemId(DRIVE_KEY, folderName);

        assertThatThrownBy(() -> service.downloadFile("1", DRIVE_KEY, itemId, user))
                .isInstanceOf(DownloadFolderException.class);
    }

    // -----------------------------------------------------------------------
    // Property 13: Non-existent item returns 404
    // For any download request where the itemId does not correspond to an
    // existing file or folder, the service SHALL throw
    // DriveItemNotFoundException.
    //
    // Validates: Requirements 5.4, 6.4, 7.4
    // -----------------------------------------------------------------------

    @Property(tries = 100)
    void nonExistentItemDownloadReturns404(
            @ForAll("nonExistentItemIds") String itemId
    ) throws IOException {
        tempDir = Files.createTempDirectory("error-nonexistent-item-");
        MyDriveService service = createService();
        User user = createUser();

        assertThatThrownBy(() -> service.downloadFile("1", DRIVE_KEY, itemId, user))
                .isInstanceOf(DriveItemNotFoundException.class);
    }

    @Provide
    Arbitrary<String> nonExistentItemIds() {
        // Generate random strings that won't match any real item ID or "root"
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(30)
                .filter(s -> !"root".equals(s));
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
