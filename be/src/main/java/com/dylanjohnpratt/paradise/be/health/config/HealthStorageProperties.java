package com.dylanjohnpratt.paradise.be.health.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "health.storage")
public record HealthStorageProperties(
    String documents,
    long maxBytes
) {}
