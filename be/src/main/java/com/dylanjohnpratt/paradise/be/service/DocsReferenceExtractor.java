package com.dylanjohnpratt.paradise.be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the file references a note or canvas makes, for the embed reference gate.
 * {@code /docs/embed} may resolve files anywhere in the enclosing Obsidian vault, so it only serves a
 * target that the {@code from} document actually references. Pure and static; {@link DocsService}
 * caches the result per file.
 * <p>
 * Every regex is bounded, never crosses a newline and repeats without recursion, so a 10 MB note with
 * inline {@code data:} images is scanned in linear time and constant stack depth.
 */
public final class DocsReferenceExtractor {

    /** {@code [[target]]} and {@code ![[target]]}. */
    private static final Pattern WIKI = Pattern.compile("!?\\[\\[([^\\[\\]\\n]{1,1000})]]");

    /**
     * Destination of {@code [text](dest)} / {@code ![alt](dest)}: {@code <...>} or a run with balanced parens.
     * The run's repetition is possessive ({@code {1,2048}+}): a greedy repeat of a group with alternation
     * recurses once per character in {@code java.util.regex} and overflows the stack on a long
     * {@code data:} URI. Nothing follows the run, so it never needs to backtrack.
     */
    private static final Pattern MD_LINK = Pattern.compile(
            "]\\([ \\t]{0,16}(?:<([^<>\\n]{1,2048})>|((?:[^\\s()]|\\([^\\s()]{0,256}\\)){1,2048}+))");

    /** Reference-style definition {@code [id]: dest}. */
    private static final Pattern MD_REF_DEF = Pattern.compile(
            "(?m)^ {0,3}\\[[^\\]\\n]{1,1000}]:[ \\t]{0,16}(?:<([^<>\\n]{1,2048})>|(\\S{1,2048}))");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DocsReferenceExtractor() {}

    /**
     * Raw link and embed targets in a Markdown document: the inside of {@code [[...]]} / {@code ![[...]]}
     * (alias and heading suffixes kept), and link/image destinations both raw and percent-decoded.
     */
    public static Set<String> markdownTargets(String markdown) {
        Set<String> targets = new LinkedHashSet<>();
        if (markdown == null || markdown.isEmpty()) {
            return targets;
        }
        Matcher wiki = WIKI.matcher(markdown);
        while (wiki.find()) {
            targets.add(wiki.group(1));
        }
        addDestinations(MD_LINK.matcher(markdown), targets);
        addDestinations(MD_REF_DEF.matcher(markdown), targets);
        return targets;
    }

    /**
     * Raw targets in an Obsidian canvas: every node's {@code file} value, plus the Markdown targets of
     * every {@code text} node. Invalid JSON yields an empty set.
     */
    public static Set<String> canvasTargets(String json) {
        Set<String> targets = new LinkedHashSet<>();
        if (json == null || json.isBlank()) {
            return targets;
        }
        JsonNode nodes;
        try {
            nodes = MAPPER.readTree(json).path("nodes");
        } catch (Exception e) {
            return targets;
        }
        if (!nodes.isArray()) {
            return targets;
        }
        for (JsonNode node : nodes) {
            JsonNode file = node.get("file");
            if (file != null && file.isTextual()) {
                targets.add(file.asText());
            }
            JsonNode text = node.get("text");
            if (text != null && text.isTextual()) {
                targets.addAll(markdownTargets(text.asText()));
            }
        }
        return targets;
    }

    /**
     * The reference gate set: {@link DocsFileTypes#key(String)} of every target that cleans to a pdf or
     * image path, cleaned both as Obsidian syntax and literally, so a request made either way matches.
     *
     * @param content the document text
     * @param canvas  true for a {@code .canvas} document, false for Markdown
     */
    public static Set<String> referenceKeys(String content, boolean canvas) {
        Set<String> raw = canvas ? canvasTargets(content) : markdownTargets(content);
        Set<String> keys = new LinkedHashSet<>();
        for (String target : raw) {
            addKey(target, false, keys);
            addKey(target, true, keys);
        }
        return Set.copyOf(keys);
    }

    private static void addKey(String target, boolean literal, Set<String> keys) {
        try {
            keys.add(DocsFileTypes.key(DocsEmbedTargetCleaner.clean(target, literal)));
        } catch (RuntimeException ignored) {
            // Not an embeddable file (a note link, a URL, or an unsafe path); not part of the gate.
        }
    }

    private static void addDestinations(Matcher matcher, Set<String> targets) {
        while (matcher.find()) {
            String dest = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (dest == null || dest.isBlank()) {
                continue;
            }
            targets.add(dest);
            String decoded = percentDecode(dest);
            if (decoded != null) {
                targets.add(decoded);
            }
        }
    }

    /**
     * Decodes {@code %XX} escapes as UTF-8. Unlike {@code URLDecoder}, a {@code +} stays a plus.
     *
     * @return the decoded string, or null when there is nothing to decode or an escape is malformed
     */
    static String percentDecode(String s) {
        if (s.indexOf('%') < 0) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); ) {
            char c = s.charAt(i);
            if (c == '%') {
                if (i + 2 >= s.length()) {
                    return null;
                }
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) {
                    return null;
                }
                out.write((hi << 4) | lo);
                i += 3;
            } else {
                int cp = s.codePointAt(i);
                byte[] bytes = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
                out.write(bytes, 0, bytes.length);
                i += Character.charCount(cp);
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
