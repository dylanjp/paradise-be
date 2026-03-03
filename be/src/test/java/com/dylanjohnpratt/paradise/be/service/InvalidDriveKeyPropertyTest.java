package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.exception.InvalidDriveKeyException;
import com.dylanjohnpratt.paradise.be.model.DriveKey;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import net.jqwik.api.*;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// Feature: my-drive-backend, Property 4: Invalid driveKey rejection
class InvalidDriveKeyPropertyTest {

    private static final Set<String> VALID_KEYS = Set.of("myDrive", "sharedDrive", "adminDrive", "mediaCache");

    private final MyDriveService service = new MyDriveService(
            mock(ItemMetadataRepository.class),
            mock(DrivePathProperties.class),
            new DriveCacheManager(new com.dylanjohnpratt.paradise.be.config.DriveCacheProperties(null, false, false, false, false))
    );

    /**
     * For any string that is not a valid drive key, DriveKey.fromString() returns null,
     * and checkPermission with a null DriveKey throws InvalidDriveKeyException.
     *
     * Validates: Requirements 2.6
     */
    @Property(tries = 100)
    void invalidDriveKeyThrowsException(@ForAll String randomString) {
        Assume.that(!VALID_KEYS.contains(randomString));

        DriveKey parsed = DriveKey.fromString(randomString);

        User user = createUser(1L, Set.of("ROLE_USER"));

        assertThatThrownBy(() ->
                service.checkPermission(parsed, "1", user, false))
                .isInstanceOf(InvalidDriveKeyException.class);
    }

    private User createUser(Long id, Set<String> roles) {
        User user = new User("user" + id, "password", roles);
        user.setId(id);
        return user;
    }
}
