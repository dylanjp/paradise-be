package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.model.DriveKey;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// Feature: my-drive-backend, Property 16: MyDrive path resolution appends userId
class DrivePathResolverPropertyTest {

    private static final String BASE_PATH = "/drives/myDrive";

    private final MyDriveService service = new MyDriveService(
            mock(ItemMetadataRepository.class),
            new DrivePathProperties(BASE_PATH, "/drives/shared", "/drives/admin", "/drives/media", "/drives/plex")
    );

    /**
     * For any userId, resolving myDrive path produces basePath/userId.
     * Validates: Requirements 8.3
     */
    @Property(tries = 100)
    void myDrivePathResolutionAppendsUserId(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String userId) {

        Path resolved = service.resolveDrivePath(DriveKey.myDrive, userId);

        assertThat(resolved).isEqualTo(Path.of(BASE_PATH).resolve(userId));
    }
}
