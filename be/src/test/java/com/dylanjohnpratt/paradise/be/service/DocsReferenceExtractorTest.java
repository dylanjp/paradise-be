package com.dylanjohnpratt.paradise.be.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Unit tests for {@link DocsReferenceExtractor}, the source of the embed reference gate.
 */
class DocsReferenceExtractorTest {

    private static final String AMIRA_ART =
            "IP Management/Lamaryah WorldBuilding/Attachments/Pictures/Amira/Official_Amira_Artwork2.jpg";

    /** Small enough that a regex recursing once per character overflows, JIT-compiled or not. */
    private static final long SMALL_STACK_BYTES = 256 * 1024;

    /**
     * Runs {@code task} on a fresh thread with a {@link #SMALL_STACK_BYTES} stack, so the result does not
     * depend on the caller's stack depth or on how far the JIT has warmed up the regex classes.
     */
    private static <T> T onSmallStack(Duration timeout, Callable<T> task) throws InterruptedException {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(null, () -> {
            try {
                result.set(task.call());
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "docs-small-stack", SMALL_STACK_BYTES);
        thread.setDaemon(true);
        thread.start();
        thread.join(timeout.toMillis());
        if (thread.isAlive()) {
            thread.interrupt();
            fail("did not finish within " + timeout);
        }
        if (failure.get() != null) {
            throw new AssertionError("failed on a small-stack thread", failure.get());
        }
        return result.get();
    }

    @Test
    @DisplayName("Amira sample: embeds and wiki links are extracted, tags and plain text are not")
    void amiraSampleTargets() {
        Set<String> targets = DocsReferenceExtractor.markdownTargets(DocsFixture.AMIRA_SAMPLE);

        assertThat(targets).containsExactlyInAnyOrder(
                AMIRA_ART,
                "Amira Artwork",
                "Faithkeeper",
                "Honor's Guard",
                "IP Management/Lamaryah WorldBuilding/Characters/The 7 Atrocities/Daken/Daken");
        assertThat(targets).noneMatch(t -> t.startsWith("#"));
    }

    @Test
    @DisplayName("Amira sample: only the embedded image enters the gate, as a normalized key")
    void amiraSampleKeys() {
        assertThat(DocsReferenceExtractor.referenceKeys(DocsFixture.AMIRA_SAMPLE, false))
                .containsExactly(DocsFileTypes.key(AMIRA_ART));
    }

    @Test
    @DisplayName("wiki embeds: alias, size and heading suffixes are cleaned; case and NFC are folded")
    void wikiSuffixes() {
        String md = "![[pic.png|300]] ![[Doc.PDF#page=2]] [[linked.jpg|Alias]] ![[Cafe\u0301.png]]";
        assertThat(DocsReferenceExtractor.referenceKeys(md, false))
                .contains("pic.png", "doc.pdf", "linked.jpg", "caf\u00e9.png")
                .doesNotContain("pic.png|300");
    }

    @Test
    @DisplayName("markdown links and images: raw and percent-decoded, angle brackets, titles, balanced parens")
    void markdownDestinations() {
        String md = String.join("\n",
                "![alt](Attachments/My%20Pic.png \"title\")",
                "![x](<Attachments/Other Pic.png>)",
                "![y](Attachments/Pic%20(1).png)",
                "[doc](files/manual.pdf)",
                "[site](https://example.com/page)",
                "[logo]: images/logo.svg",
                "  [bad]: <images/space name.webp>");
        Set<String> keys = DocsReferenceExtractor.referenceKeys(md, false);

        assertThat(keys).contains(
                "attachments/my%20pic.png",
                "attachments/my pic.png",
                "attachments/other pic.png",
                "attachments/pic (1).png",
                "attachments/pic%20(1).png",
                "files/manual.pdf",
                "images/logo.svg",
                "images/space name.webp");
        assertThat(keys).noneMatch(k -> k.contains("example.com/page"));
    }

    @Test
    @DisplayName("note links and non-file targets never enter the gate")
    void nonBinaryExcluded() {
        String md = "[[Faithkeeper]] [[Daken.md]] ![[Board.canvas]] [x](other.md) ![[C:/secret.png]]";
        assertThat(DocsReferenceExtractor.referenceKeys(md, false)).isEmpty();
    }

    @Test
    @DisplayName("canvas: file values (literal, keeping #) and markdown inside text nodes")
    void canvasTargets() {
        String canvas = """
                {"nodes":[
                  {"id":"1","type":"file","file":"Attachments/Images/Concept/Characters/Amira/#ClairLineArt.jpg"},
                  {"id":"2","type":"file","file":"IP Management/Lamaryah WorldBuilding/Characters/Amira/Amira.md"},
                  {"id":"3","type":"text","text":"# Title\\n![[inner.png|100]] and [[Some Note]]"},
                  {"id":"4","type":"link","url":"https://example.com/x.png"},
                  {"id":"5","type":"group","label":"Group"}
                ],"edges":[{"id":"e","fromNode":"1","toNode":"2","fromEnd":"arrow"}]}
                """;
        assertThat(DocsReferenceExtractor.canvasTargets(canvas)).containsExactlyInAnyOrder(
                "Attachments/Images/Concept/Characters/Amira/#ClairLineArt.jpg",
                "IP Management/Lamaryah WorldBuilding/Characters/Amira/Amira.md",
                "inner.png|100",
                "Some Note");
        // "#ClairLineArt.jpg" survives only the literal cleaning; "inner.png|100" only the Obsidian one.
        assertThat(DocsReferenceExtractor.referenceKeys(canvas, true)).containsExactlyInAnyOrder(
                "attachments/images/concept/characters/amira/#clairlineart.jpg",
                "inner.png");
    }

    @Test
    @DisplayName("empty, {} and invalid canvases yield nothing")
    void emptyAndInvalidCanvas() {
        assertThat(DocsReferenceExtractor.referenceKeys("", true)).isEmpty();
        assertThat(DocsReferenceExtractor.referenceKeys("{}", true)).isEmpty();
        assertThat(DocsReferenceExtractor.referenceKeys("{\"nodes\":{}}", true)).isEmpty();
        assertThat(DocsReferenceExtractor.referenceKeys("{not json", true)).isEmpty();
        assertThat(DocsReferenceExtractor.referenceKeys(null, false)).isEmpty();
    }

    @Test
    @DisplayName("percent-decoding keeps + and rejects malformed escapes")
    void percentDecode() {
        assertThat(DocsReferenceExtractor.percentDecode("a%20b+c.png")).isEqualTo("a b+c.png");
        assertThat(DocsReferenceExtractor.percentDecode("caf%C3%A9.png")).isEqualTo("caf\u00e9.png");
        assertThat(DocsReferenceExtractor.percentDecode("bad%2.png")).isNull();
        assertThat(DocsReferenceExtractor.percentDecode("plain.png")).isNull();
    }

    @Test
    @DisplayName("Obsidian's table-escaped pipe: '![[pic.png\\|300]]' in a table row gates pic.png")
    void escapedPipeInTable() {
        String md = String.join("\n",
                "| Gallery | Size |",
                "| --- | --- |",
                "| ![[pic.png\\|300]] | 300 |",
                "| ![[Attachments/Sub Dir/other.jpg\\|alt\\|200]] | 200 |");

        assertThat(DocsReferenceExtractor.markdownTargets(md))
                .contains("pic.png\\|300", "Attachments/Sub Dir/other.jpg\\|alt\\|200");
        assertThat(DocsReferenceExtractor.referenceKeys(md, false))
                .contains("pic.png", "attachments/sub dir/other.jpg")
                .noneMatch(k -> k.endsWith("/") || k.contains("|"));
    }

    @Test
    @DisplayName("a 10 MB note with an inline data: image is scanned quickly, even on a small stack")
    void largeNoteIsLinear() throws InterruptedException {
        StringBuilder sb = new StringBuilder("![[real.png]]\n![inline](data:image/png;base64,");
        sb.append("A".repeat(10 * 1024 * 1024));
        sb.append(")\n").append("[[".repeat(50_000)).append("\n](".repeat(50_000));
        String md = sb.toString();

        Set<String> keys = onSmallStack(Duration.ofSeconds(10),
                () -> DocsReferenceExtractor.referenceKeys(md, false));
        assertThat(keys).contains("real.png");
    }

    @Test
    @DisplayName("a long data: URI destination does not overflow the stack; later links are still found")
    void longDataUriDestination() throws InterruptedException {
        String dataUri = "data:image/png;base64," + "iVBORw0KGgo".repeat(1000);
        String md = String.join("\n",
                "![[before.png]]",
                "![pasted](" + dataUri + ")",
                "![after](Attachments/Pic%20(1).png)",
                "| ![[pic.png|300]] |");
        String canvas = "{\"nodes\":[{\"id\":\"1\",\"type\":\"text\",\"text\":\"![x](" + dataUri
                + ")\\n![[canvas.png]]\"}]}";

        Set<String> keys = onSmallStack(Duration.ofSeconds(10),
                () -> DocsReferenceExtractor.referenceKeys(md, false));
        assertThat(keys).contains("before.png", "attachments/pic (1).png", "pic.png")
                .noneMatch(k -> k.startsWith("data:"));

        Set<String> canvasKeys = onSmallStack(Duration.ofSeconds(10),
                () -> DocsReferenceExtractor.referenceKeys(canvas, true));
        assertThat(canvasKeys).containsExactly("canvas.png");
    }
}
