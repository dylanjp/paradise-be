package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.dto.DocsTreeNode;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * In-memory cache for the documentation file tree.
 * Stores a single cached DocsTreeNode root with a last-refresh timestamp.
 * Thread-safe via volatile fields.
 */
@Component
public class DocsCacheManager {

    private volatile DocsTreeNode cachedTree;
    private volatile Instant lastRefresh;

    public Optional<DocsTreeNode> get() {
        return Optional.ofNullable(cachedTree);
    }

    public void put(DocsTreeNode tree) {
        this.cachedTree = tree;
        this.lastRefresh = Instant.now();
    }

    public Instant getLastRefresh() {
        return lastRefresh;
    }
}
