package com.dylanjohnpratt.paradise.be.model;

/**
 * Enum representing the four virtual drive types available in the application.
 * Each drive type has a different permission model and filesystem root:
 * <ul>
 *   <li>{@code myDrive} — per-user personal drive, only accessible by the owning user</li>
 *   <li>{@code sharedDrive} — shared drive accessible to all authenticated users</li>
 *   <li>{@code adminDrive} — administrative drive requiring ROLE_ADMIN</li>
 *   <li>{@code mediaCache} — read-only drive for cached media content</li>
 * </ul>
 */
public enum DriveKey {
    myDrive, sharedDrive, adminDrive, mediaCache;

    public static DriveKey fromString(String key) {
        try {
            return DriveKey.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
