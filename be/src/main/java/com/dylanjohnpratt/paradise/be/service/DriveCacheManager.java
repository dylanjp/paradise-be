package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DriveCacheProperties;
import com.dylanjohnpratt.paradise.be.dto.DriveItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory caching of drive contents (flat map responses).
 * Drives like mediaCache and sharedDrive use a global cache key (shared across all users),
 * while per-user drives like myDrive use a userId-scoped key.
 */
@Component
public class DriveCacheManager {

    private static final Logger log = LoggerFactory.getLogger(DriveCacheManager.class);

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final DriveCacheProperties cacheProperties;

    public DriveCacheManager(DriveCacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    /**
     * Returns true if the given drive maps to a shared filesystem
     * that is identical for all users (no per-user path component).
     */
    boolean isGlobalCache(String driveKey) {
        return switch (driveKey) {
            case "mediaCache", "sharedDrive" -> true;
            default -> false;
        };
    }

    /**
     * Computes the cache key for a given user and drive combination.
     * Global drives use a "global:{driveKey}" key shared across all users,
     * while per-user drives use "{userId}:{driveKey}".
     *
     * @param userId   the user ID (ignored for global drives)
     * @param driveKey the drive type identifier
     * @return the computed cache key string
     */
    String cacheKey(String userId, String driveKey) {
        if (isGlobalCache(driveKey)) {
            return "global:" + driveKey;
        }
        return userId + ":" + driveKey;
    }

    /**
     * Checks whether caching is enabled for the specified drive type.
     * Each drive type can be individually enabled or disabled via
     * {@link DriveCacheProperties} configuration.
     *
     * @param driveKey the drive type identifier
     * @return true if caching is enabled for this drive type
     */
    public boolean isEnabled(String driveKey) {
        return switch (driveKey) {
            case "myDrive" -> cacheProperties.myDrive();
            case "sharedDrive" -> cacheProperties.sharedDrive();
            case "adminDrive" -> cacheProperties.adminDrive();
            case "mediaCache" -> cacheProperties.mediaCache();
            default -> false;
        };
    }

    /**
     * Retrieves cached drive contents for the given user and drive.
     * Returns empty if caching is disabled, the entry is absent, or the entry
     * has exceeded the configured TTL. Errors are logged and treated as cache misses.
     *
     * @param userId   the user ID (used for per-user cache keys)
     * @param driveKey the drive type identifier
     * @return the cached flat map of item IDs to DriveItems, or empty if not cached/stale
     */
    public Optional<Map<String, DriveItem>> get(String userId, String driveKey) {
        try {
            if (!isEnabled(driveKey)) {
                return Optional.empty();
            }
            String key = cacheKey(userId, driveKey);
            CacheEntry entry = cache.get(key);
            if (entry == null || entry.isStale(cacheProperties.ttl())) {
                return Optional.empty();
            }
            return Optional.of(entry.contents());
        } catch (Exception e) {
            log.error("Error retrieving cache entry for userId={}, driveKey={}", userId, driveKey, e);
            return Optional.empty();
        }
    }

    /**
     * Stores drive contents in the cache for the given user and drive.
     * Uses the appropriate cache key (global or per-user) based on the drive type.
     *
     * @param userId   the user ID (used for per-user cache keys)
     * @param driveKey the drive type identifier
     * @param contents the flat map of item IDs to DriveItems to cache
     */
    public void put(String userId, String driveKey, Map<String, DriveItem> contents) {
        String key = cacheKey(userId, driveKey);
        cache.put(key, new CacheEntry(contents, Instant.now()));
    }

    /**
     * Stores drive contents in the global cache slot, bypassing per-user key logic.
     * Used by cache warming operations that populate shared drive caches.
     *
     * @param driveKey the drive type identifier
     * @param contents the flat map of item IDs to DriveItems to cache
     */
    void putGlobal(String driveKey, Map<String, DriveItem> contents) {
        String key = "global:" + driveKey;
        cache.put(key, new CacheEntry(contents, Instant.now()));
    }

    /**
     * Removes the cached entry for the given user and drive.
     * Called after any write operation (create, upload, update, move, delete) to ensure
     * subsequent reads reflect the latest filesystem state.
     *
     * @param userId   the user ID (used for per-user cache keys)
     * @param driveKey the drive type identifier
     */
    public void invalidate(String userId, String driveKey) {
        String key = cacheKey(userId, driveKey);
        cache.remove(key);
    }

    /**
     * Removes the global cached entry for the specified drive.
     * Used when a global drive's contents change and the shared cache needs to be cleared.
     *
     * @param driveKey the drive type identifier
     */
    public void invalidateGlobal(String driveKey) {
        String key = "global:" + driveKey;
        cache.remove(key);
    }
}
