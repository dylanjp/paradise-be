package com.dylanjohnpratt.paradise.be.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DriveKeyTest {

    @Test
    void fromString_validKeys_returnsCorrectEnum() {
        assertThat(DriveKey.fromString("myDrive")).isEqualTo(DriveKey.myDrive);
        assertThat(DriveKey.fromString("sharedDrive")).isEqualTo(DriveKey.sharedDrive);
        assertThat(DriveKey.fromString("adminDrive")).isEqualTo(DriveKey.adminDrive);
        assertThat(DriveKey.fromString("mediaCache")).isEqualTo(DriveKey.mediaCache);
    }

    @Test
    void fromString_invalidKey_returnsNull() {
        assertThat(DriveKey.fromString("invalidKey")).isNull();
        assertThat(DriveKey.fromString("")).isNull();
        assertThat(DriveKey.fromString("MYDRIVE")).isNull();
    }

    @Test
    void enumValues_containsExactlyFourKeys() {
        assertThat(DriveKey.values()).containsExactly(
            DriveKey.myDrive, DriveKey.sharedDrive, DriveKey.adminDrive, DriveKey.mediaCache
        );
    }
}
