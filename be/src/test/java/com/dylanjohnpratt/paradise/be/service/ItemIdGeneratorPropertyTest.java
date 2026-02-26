package com.dylanjohnpratt.paradise.be.service;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: my-drive-backend, Property 15: Deterministic ID generation
class ItemIdGeneratorPropertyTest {

    /**
     * Same inputs always produce the same output (deterministic).
     * Validates: Requirements 10.1, 10.2
     */
    @Property(tries = 100)
    void sameInputsAlwaysProduceSameId(@ForAll String driveKey, @ForAll String relativePath) {
        String id1 = MyDriveService.generateItemId(driveKey, relativePath);
        String id2 = MyDriveService.generateItemId(driveKey, relativePath);
        assertThat(id1).isEqualTo(id2);
    }

    /**
     * Distinct (driveKey, relativePath) pairs produce distinct IDs (collision resistance).
     * Filters out root-representing paths since those all map to "root".
     * Validates: Requirements 10.1, 10.2
     */
    @Property(tries = 100)
    void distinctInputsProduceDistinctIds(
            @ForAll String driveKey1, @ForAll String path1,
            @ForAll String driveKey2, @ForAll String path2) {

        Assume.that(!isRootPath(path1));
        Assume.that(!isRootPath(path2));
        Assume.that(!driveKey1.equals(driveKey2) || !path1.equals(path2));

        String id1 = MyDriveService.generateItemId(driveKey1, path1);
        String id2 = MyDriveService.generateItemId(driveKey2, path2);
        assertThat(id1).isNotEqualTo(id2);
    }

    private boolean isRootPath(String path) {
        return path == null || path.isEmpty() || path.equals("/") || path.equals(".");
    }
}
