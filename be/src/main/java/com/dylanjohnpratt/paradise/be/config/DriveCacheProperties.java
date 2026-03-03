package com.dylanjohnpratt.paradise.be.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "drive.cache")
public record DriveCacheProperties(
    @DefaultValue("PT2H") Duration ttl,
    @DefaultValue("false") boolean myDrive,
    @DefaultValue("false") boolean sharedDrive,
    @DefaultValue("false") boolean adminDrive,
    @DefaultValue("true") boolean mediaCache
) {}
