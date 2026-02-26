package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.model.DriveKey;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DrivePathResolverTest {

    private MyDriveService service;

    @BeforeEach
    void setUp() {
        DrivePathProperties props = new DrivePathProperties(
            "/drives/my", "/drives/shared", "/drives/admin", "/drives/media", "/drives/plex"
        );
        service = new MyDriveService(mock(ItemMetadataRepository.class), props);
    }

    @Test
    void resolve_myDrive_appendsUserId() {
        Path result = service.resolveDrivePath(DriveKey.myDrive, "user123");
        assertThat(result).isEqualTo(Path.of("/drives/my/user123"));
    }

    @Test
    void resolve_sharedDrive_returnsBasePath() {
        Path result = service.resolveDrivePath(DriveKey.sharedDrive, "user123");
        assertThat(result).isEqualTo(Path.of("/drives/shared"));
    }

    @Test
    void resolve_adminDrive_returnsBasePath() {
        Path result = service.resolveDrivePath(DriveKey.adminDrive, "user123");
        assertThat(result).isEqualTo(Path.of("/drives/admin"));
    }

    @Test
    void resolve_mediaCache_returnsBasePath() {
        Path result = service.resolveDrivePath(DriveKey.mediaCache, "user123");
        assertThat(result).isEqualTo(Path.of("/drives/media"));
    }
}
