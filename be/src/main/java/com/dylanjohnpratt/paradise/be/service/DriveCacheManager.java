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
 * Manages in-memory caching of drive contents (flat map responses)
 * keyed by userId:driveKey. Supports configurable TTL and per-drive-key toggles.
 */
@Component
public class DriveCacheManager {

    private static final Logger log = LoggerFactory.getLogger(DriveCacheManager.class);

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final DriveCacheProperties cacheProperties;

    public DriveCacheManager(DriveCacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    String cacheKey(String userId, String driveKey) {
        return userId + ":" + driveKey;
    }

    public boolean isEnabled(String driveKey) {
        return switch (driveKey) {
            case "myDrive" -> cacheProperties.myDrive();
            case "sharedDrive" -> cacheProperties.sharedDrive();
            case "adminDrive" -> cacheProperties.adminDrive();
            case "mediaCache" -> cacheProperties.mediaCache();
            default -> false;
        };
    }

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

    public void put(String userId, String driveKey, Map<String, DriveItem> contents) {
        String key = cacheKey(userId, driveKey);
        cache.put(key, new CacheEntry(contents, Instant.now()));
    }

    public void invalidate(String userId, String driveKey) {
        String key = cacheKey(userId, driveKey);
        cache.remove(key);
    }
}
