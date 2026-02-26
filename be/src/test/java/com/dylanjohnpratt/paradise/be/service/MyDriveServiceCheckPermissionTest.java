package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.exception.DriveAccessDeniedException;
import com.dylanjohnpratt.paradise.be.exception.InvalidDriveKeyException;
import com.dylanjohnpratt.paradise.be.model.DriveKey;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class MyDriveServiceCheckPermissionTest {

    private MyDriveService service;

    @BeforeEach
    void setUp() {
        ItemMetadataRepository repository = mock(ItemMetadataRepository.class);
        service = new MyDriveService(repository, mock(DrivePathProperties.class));
    }

    private User createUser(Long id, Set<String> roles) {
        User user = new User("testuser", "password", roles);
        user.setId(id);
        return user;
    }

    // --- null driveKey ---

    @Test
    void checkPermission_nullDriveKey_throwsInvalidDriveKeyException() {
        User user = createUser(1L, Set.of());
        assertThatThrownBy(() -> service.checkPermission(null, "1", user, false))
                .isInstanceOf(InvalidDriveKeyException.class);
    }

    // --- myDrive ---

    @Test
    void checkPermission_myDrive_ownerAllowed() {
        User user = createUser(1L, Set.of());
        assertThatCode(() -> service.checkPermission(DriveKey.myDrive, "1", user, false))
                .doesNotThrowAnyException();
    }

    @Test
    void checkPermission_myDrive_ownerWriteAllowed() {
        User user = createUser(1L, Set.of());
        assertThatCode(() -> service.checkPermission(DriveKey.myDrive, "1", user, true))
                .doesNotThrowAnyException();
    }

    @Test
    void checkPermission_myDrive_nonOwnerDenied() {
        User user = createUser(2L, Set.of());
        assertThatThrownBy(() -> service.checkPermission(DriveKey.myDrive, "1", user, false))
                .isInstanceOf(DriveAccessDeniedException.class);
    }

    // --- sharedDrive ---

    @Test
    void checkPermission_sharedDrive_anyUserReadAllowed() {
        User user = createUser(1L, Set.of());
        assertThatCode(() -> service.checkPermission(DriveKey.sharedDrive, "1", user, false))
                .doesNotThrowAnyException();
    }

    @Test
    void checkPermission_sharedDrive_anyUserWriteAllowed() {
        User user = createUser(1L, Set.of());
        assertThatCode(() -> service.checkPermission(DriveKey.sharedDrive, "1", user, true))
                .doesNotThrowAnyException();
    }

    // --- adminDrive ---

    @Test
    void checkPermission_adminDrive_adminAllowed() {
        User user = createUser(1L, Set.of("ROLE_ADMIN"));
        assertThatCode(() -> service.checkPermission(DriveKey.adminDrive, "1", user, false))
                .doesNotThrowAnyException();
    }

    @Test
    void checkPermission_adminDrive_nonAdminDenied() {
        User user = createUser(1L, Set.of("ROLE_USER"));
        assertThatThrownBy(() -> service.checkPermission(DriveKey.adminDrive, "1", user, false))
                .isInstanceOf(DriveAccessDeniedException.class);
    }

    // --- mediaCache ---

    @Test
    void checkPermission_mediaCache_readAllowed() {
        User user = createUser(1L, Set.of());
        assertThatCode(() -> service.checkPermission(DriveKey.mediaCache, "1", user, false))
                .doesNotThrowAnyException();
    }

    @Test
    void checkPermission_mediaCache_writeDenied() {
        User user = createUser(1L, Set.of());
        assertThatThrownBy(() -> service.checkPermission(DriveKey.mediaCache, "1", user, true))
                .isInstanceOf(DriveAccessDeniedException.class);
    }
}
