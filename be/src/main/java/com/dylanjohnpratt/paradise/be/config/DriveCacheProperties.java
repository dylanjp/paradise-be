package com.dylanjohnpratt.paradise.be.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the drive in-memory cache.
 * Controls the cache time-to-live (TTL) and per-drive-type enable/disable flags.
 * Bound from {@code drive.cache.*} properties in application.properties.
 * Caching is enabled by default only for mediaCache (read-only, shared content).
 *
 * @param ttl         how long cached entries remain valid before being considered stale (default: 2 hours)
 * @param myDrive     whether to cache per-user myDrive contents (default: false)
 * @param sharedDrive whether to cache sharedDrive contents (default: false)
 * @param adminDrive  whether to cache adminDrive contents (default: false)
 * @param mediaCache  whether to cache mediaCache contents (default: true)
 */
@ConfigurationProperties(prefix = "drive.cache")
public record DriveCacheProperties(
    @DefaultValue("PT2H") Duration ttl,
    @DefaultValue("false") boolean myDrive,
    @DefaultValue("false") boolean sharedDrive,
    @DefaultValue("false") boolean adminDrive,
    @DefaultValue("true") boolean mediaCache
) {}
