package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DrivePathProperties;
import com.dylanjohnpratt.paradise.be.exception.DriveAccessDeniedException;
import com.dylanjohnpratt.paradise.be.model.DriveKey;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.ItemMetadataRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// Feature: my-drive-backend, Property 3: Drive access permissions
class DriveAccessPermissionsPropertyTest {

    private final MyDriveService service = new MyDriveService(
            mock(ItemMetadataRepository.class),
            mock(DrivePathProperties.class)
    );

    /**
     * myDrive: allowed iff currentUser.getId().toString().equals(userId).
     * Validates: Requirements 2.1
     */
    @Property(tries = 100)
    void myDriveAllowedOnlyWhenUserIdMatches(
            @ForAll @LongRange(min = 1, max = 10000) Long userId,
            @ForAll @LongRange(min = 1, max = 10000) Long currentUserId,
            @ForAll boolean isWrite) {

        User user = createUser(currentUserId, Set.of());
        String userIdStr = userId.toString();

        if (currentUserId.equals(userId)) {
            // Should not throw
            service.checkPermission(DriveKey.myDrive, userIdStr, user, isWrite);
        } else {
            assertThatThrownBy(() ->
                    service.checkPermission(DriveKey.myDrive, userIdStr, user, isWrite))
                    .isInstanceOf(DriveAccessDeniedException.class);
        }
    }

    /**
     * sharedDrive: always allowed for any authenticated user (read and write).
     * Validates: Requirements 2.2
     */
    @Property(tries = 100)
    void sharedDriveAlwaysAllowed(
            @ForAll("users") User user,
            @ForAll String userId,
            @ForAll boolean isWrite) {

        // Should never throw
        service.checkPermission(DriveKey.sharedDrive, userId, user, isWrite);
    }

    /**
     * adminDrive: allowed iff user has ROLE_ADMIN.
     * Validates: Requirements 2.3
     */
    @Property(tries = 100)
    void adminDriveAllowedOnlyForAdmins(
            @ForAll("users") User user,
            @ForAll String userId,
            @ForAll boolean isWrite) {

        boolean isAdmin = user.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            service.checkPermission(DriveKey.adminDrive, userId, user, isWrite);
        } else {
            assertThatThrownBy(() ->
                    service.checkPermission(DriveKey.adminDrive, userId, user, isWrite))
                    .isInstanceOf(DriveAccessDeniedException.class);
        }
    }

    /**
     * mediaCache: read always allowed, write always denied.
     * Validates: Requirements 2.4, 2.5
     */
    @Property(tries = 100)
    void mediaCacheReadOnlyAccess(
            @ForAll("users") User user,
            @ForAll String userId) {

        // Read should always succeed
        service.checkPermission(DriveKey.mediaCache, userId, user, false);

        // Write should always be denied
        assertThatThrownBy(() ->
                service.checkPermission(DriveKey.mediaCache, userId, user, true))
                .isInstanceOf(DriveAccessDeniedException.class);
    }

    /**
     * Full permission matrix: for any user, userId, driveKey, and isWrite flag,
     * the permission check matches the expected matrix.
     * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5
     */
    @Property(tries = 100)
    void fullPermissionMatrix(
            @ForAll("driveKeys") DriveKey driveKey,
            @ForAll @LongRange(min = 1, max = 10000) Long userId,
            @ForAll @LongRange(min = 1, max = 10000) Long currentUserId,
            @ForAll boolean hasAdminRole,
            @ForAll boolean isWrite) {

        Set<String> roles = hasAdminRole ? Set.of("ROLE_ADMIN") : Set.of("ROLE_USER");
        User user = createUser(currentUserId, roles);
        String userIdStr = userId.toString();

        boolean shouldAllow = switch (driveKey) {
            case myDrive -> currentUserId.equals(userId);
            case sharedDrive -> true;
            case adminDrive -> hasAdminRole;
            case mediaCache -> !isWrite;
        };

        if (shouldAllow) {
            service.checkPermission(driveKey, userIdStr, user, isWrite);
        } else {
            assertThatThrownBy(() ->
                    service.checkPermission(driveKey, userIdStr, user, isWrite))
                    .isInstanceOf(DriveAccessDeniedException.class);
        }
    }

    @Provide
    Arbitrary<DriveKey> driveKeys() {
        return Arbitraries.of(DriveKey.values());
    }

    @Provide
    Arbitrary<User> users() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1, 10000);
        Arbitrary<Set<String>> roleSets = Arbitraries.of(
                Set.of(),
                Set.of("ROLE_USER"),
                Set.of("ROLE_ADMIN"),
                Set.of("ROLE_USER", "ROLE_ADMIN")
        );
        return Combinators.combine(ids, roleSets).as((id, roles) -> createUser(id, roles));
    }

    private User createUser(Long id, Set<String> roles) {
        User user = new User("user" + id, "password", roles);
        user.setId(id);
        return user;
    }
}
