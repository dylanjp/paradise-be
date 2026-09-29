package com.dylanjohnpratt.paradise.be.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * In-memory cache for the documentation snapshot (tree, active roots, embed index).
 * Stores a single {@link DocsSnapshot} with its refresh timestamp.
 * Thread-safe: both values are published together through one volatile field.
 */
@Component
public class DocsCacheManager {

    private record Entry(DocsSnapshot snapshot, Instant refreshedAt) {}

    private volatile Entry entry;

    public Optional<DocsSnapshot> get() {
        Entry current = entry;
        return current == null ? Optional.empty() : Optional.of(current.snapshot());
    }

    public void put(DocsSnapshot snapshot, Instant refreshedAt) {
        this.entry = new Entry(snapshot, refreshedAt);
    }

    /** When the cached snapshot was built, or null before the first refresh. */
    public Instant getLastRefresh() {
        Entry current = entry;
        return current == null ? null : current.refreshedAt();
    }
}
