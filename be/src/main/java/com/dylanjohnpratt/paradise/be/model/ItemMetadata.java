package com.dylanjohnpratt.paradise.be.model;

import jakarta.persistence.*;

/**
 * Persists metadata for drive items that cannot be derived from the filesystem,
 * such as folder color. Keyed by the deterministic item ID.
 */
@Entity
@Table(name = "item_metadata")
public class ItemMetadata {

    @Id
    @Column(length = 64)
    private String itemId;

    @Column(nullable = false, length = 20)
    private String driveKey;

    @Column(length = 20)
    private String color;

    public ItemMetadata() {
    }

    public ItemMetadata(String itemId, String driveKey, String color) {
        this.itemId = itemId;
        this.driveKey = driveKey;
        this.color = color;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getDriveKey() {
        return driveKey;
    }

    public void setDriveKey(String driveKey) {
        this.driveKey = driveKey;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
