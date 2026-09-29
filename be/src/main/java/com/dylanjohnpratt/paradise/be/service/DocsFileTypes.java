package com.dylanjohnpratt.paradise.be.service;

import org.springframework.http.MediaType;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * File type rules for the Documentation feature.
 * Only these extensions are listed in the tree or served: text documents (md, canvas) through
 * {@code /docs/file}, and binary previews (pdf and images) through {@code /docs/raw} and {@code /docs/embed}.
 * Media types come from an explicit map rather than {@code Files.probeContentType}, so the response type
 * never depends on the host OS registry.
 */
public final class DocsFileTypes {

    /** Extensions served as text by {@code /docs/file}. */
    public static final Set<String> TEXT_EXTENSIONS = Set.of("md", "canvas");

    /** Extensions served as binary by {@code /docs/raw} and {@code /docs/embed}. */
    public static final Set<String> BINARY_EXTENSIONS =
            Set.of("pdf", "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "avif");

    private static final Map<String, MediaType> MEDIA_TYPES = Map.ofEntries(
            Map.entry("md", MediaType.parseMediaType("text/markdown;charset=UTF-8")),
            Map.entry("canvas", MediaType.APPLICATION_JSON),
            Map.entry("pdf", MediaType.APPLICATION_PDF),
            Map.entry("png", MediaType.IMAGE_PNG),
            Map.entry("jpg", MediaType.IMAGE_JPEG),
            Map.entry("jpeg", MediaType.IMAGE_JPEG),
            Map.entry("gif", MediaType.IMAGE_GIF),
            Map.entry("webp", MediaType.parseMediaType("image/webp")),
            Map.entry("svg", MediaType.parseMediaType("image/svg+xml")),
            Map.entry("bmp", MediaType.parseMediaType("image/bmp")),
            Map.entry("avif", MediaType.parseMediaType("image/avif"))
    );

    private DocsFileTypes() {}

    /**
     * Returns the lower-cased extension of the last path segment, or "" when there is none.
     * A leading dot (".obsidian") does not count as an extension.
     */
    public static String extension(String name) {
        if (name == null) return "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String last = name.substring(slash + 1);
        int dot = last.lastIndexOf('.');
        if (dot <= 0 || dot == last.length() - 1) return "";
        return last.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** True for any extension the docs feature lists or serves. */
    public static boolean isAllowed(String name) {
        return isText(name) || isBinary(name);
    }

    /** True for md and canvas. */
    public static boolean isText(String name) {
        return TEXT_EXTENSIONS.contains(extension(name));
    }

    /** True for pdf and the supported image types. */
    public static boolean isBinary(String name) {
        return BINARY_EXTENSIONS.contains(extension(name));
    }

    /** True for svg, which gets a sandboxing Content-Security-Policy when served. */
    public static boolean isSvg(String name) {
        return "svg".equals(extension(name));
    }

    /**
     * Returns the explicit media type for an allowed file, or {@code application/octet-stream} otherwise.
     */
    public static MediaType mediaType(String name) {
        return MEDIA_TYPES.getOrDefault(extension(name), MediaType.APPLICATION_OCTET_STREAM);
    }

    /**
     * Comparison key used for every name match: NFC-normalized, {@link Locale#ROOT} lower-cased,
     * with {@code \} converted to {@code /}. Obsidian matches links case-insensitively, and macOS
     * clients may write NFD file names, so raw string equality is not enough.
     */
    public static String key(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replace('\\', '/');
    }
}
