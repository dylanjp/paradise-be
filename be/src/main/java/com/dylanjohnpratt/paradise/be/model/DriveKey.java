package com.dylanjohnpratt.paradise.be.model;

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
