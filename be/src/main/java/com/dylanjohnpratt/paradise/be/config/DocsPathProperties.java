package com.dylanjohnpratt.paradise.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docs")
public record DocsPathProperties(
    String path
) {}
