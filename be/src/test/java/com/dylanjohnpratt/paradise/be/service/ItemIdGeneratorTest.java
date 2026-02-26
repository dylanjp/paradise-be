package com.dylanjohnpratt.paradise.be.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemIdGeneratorTest {

    @Test
    void generate_emptyPath_returnsRoot() {
        assertThat(MyDriveService.generateItemId("myDrive", "")).isEqualTo("root");
    }

    @Test
    void generate_nullPath_returnsRoot() {
        assertThat(MyDriveService.generateItemId("myDrive", null)).isEqualTo("root");
    }

    @Test
    void generate_slashPath_returnsRoot() {
        assertThat(MyDriveService.generateItemId("myDrive", "/")).isEqualTo("root");
    }

    @Test
    void generate_dotPath_returnsRoot() {
        assertThat(MyDriveService.generateItemId("myDrive", ".")).isEqualTo("root");
    }

    @Test
    void generate_validPath_returnsSha256Hex() {
        String id = MyDriveService.generateItemId("myDrive", "docs/readme.txt");
        // SHA-256 hex is always 64 characters
        assertThat(id).hasSize(64);
        assertThat(id).matches("[0-9a-f]{64}");
    }

    @Test
    void generate_sameInputs_returnsSameId() {
        String id1 = MyDriveService.generateItemId("myDrive", "photos/cat.jpg");
        String id2 = MyDriveService.generateItemId("myDrive", "photos/cat.jpg");
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void generate_differentDriveKeys_returnsDifferentIds() {
        String id1 = MyDriveService.generateItemId("myDrive", "file.txt");
        String id2 = MyDriveService.generateItemId("sharedDrive", "file.txt");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void generate_differentPaths_returnsDifferentIds() {
        String id1 = MyDriveService.generateItemId("myDrive", "a.txt");
        String id2 = MyDriveService.generateItemId("myDrive", "b.txt");
        assertThat(id1).isNotEqualTo(id2);
    }
}
