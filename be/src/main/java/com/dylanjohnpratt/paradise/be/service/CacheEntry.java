package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.dto.DriveItem;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Represents a cached drive contents entry with a creation timestamp
 * for TTL-based staleness checking.
 */
public record CacheEntry(
    Map<String, DriveItem> contents,
    Instant createdAt
) {
    /**
     * Returns true if this entry has exceeded the given TTL duration.
     */
    boolean isStale(Duration ttl) {
        return Instant.now().isAfter(createdAt.plus(ttl));
    }
}
