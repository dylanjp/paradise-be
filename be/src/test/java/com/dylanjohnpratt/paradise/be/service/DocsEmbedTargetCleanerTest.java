package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.exception.DocsFileNotFoundException;
import com.dylanjohnpratt.paradise.be.exception.DocsInvalidFileTypeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DocsEmbedTargetCleaner}: Obsidian suffix stripping, normalization, and
 * rejection of anything that is not a safe relative pdf/image path.
 */
class DocsEmbedTargetCleanerTest {

    @Test
    @DisplayName("strips |alias, #heading and ^block unless literal")
    void stripsObsidianSuffixes() {
        assertThat(DocsEmbedTargetCleaner.clean("Attachments/pic.png|300", false)).isEqualTo("Attachments/pic.png");
        assertThat(DocsEmbedTargetCleaner.clean("pic.png|300x200", false)).isEqualTo("pic.png");
        assertThat(DocsEmbedTargetCleaner.clean("doc.pdf#page=3", false)).isEqualTo("doc.pdf");
        assertThat(DocsEmbedTargetCleaner.clean("pic.png^block", false)).isEqualTo("pic.png");
    }

    @Test
    @DisplayName("a backslash before the cut | is Obsidian's table pipe escape and is dropped")
    void dropsEscapedPipe() {
        assertThat(DocsEmbedTargetCleaner.clean("pic.png\\|300", false)).isEqualTo("pic.png");
        assertThat(DocsEmbedTargetCleaner.clean("Attachments\\Sub\\pic.png\\|alt\\|300", false))
                .isEqualTo("Attachments/Sub/pic.png");
        assertThat(DocsEmbedTargetCleaner.cutObsidianSuffix("a\\b.png|300")).isEqualTo("a\\b.png");
    }

    @Test
    @DisplayName("literal keeps # in canvas file names")
    void literalKeepsHash() {
        assertThat(DocsEmbedTargetCleaner.clean("Attachments/#ClairLineArt.jpg", true))
                .isEqualTo("Attachments/#ClairLineArt.jpg");
        assertThatThrownBy(() -> DocsEmbedTargetCleaner.clean("#ClairLineArt.jpg", false))
                .isInstanceOf(DocsFileNotFoundException.class);
    }

    @Test
    @DisplayName("trims, NFC-normalizes and converts backslashes")
    void normalizes() {
        assertThat(DocsEmbedTargetCleaner.clean("  pic.png\t", false)).isEqualTo("pic.png");
        assertThat(DocsEmbedTargetCleaner.clean("Cafe\u0301.png", false)).isEqualTo("Caf\u00e9.png");
        assertThat(DocsEmbedTargetCleaner.clean("a\\b\\c.png", false)).isEqualTo("a/b/c.png");
        assertThat(DocsEmbedTargetCleaner.clean("Pic.PNG", false)).isEqualTo("Pic.PNG");
    }

    @Test
    @DisplayName("leading / and ./ are stripped (vault-root relative), including //h/s UNC-looking input")
    void stripsLeadingSlashes() {
        assertThat(DocsEmbedTargetCleaner.clean("/x.png", false)).isEqualTo("x.png");
        assertThat(DocsEmbedTargetCleaner.clean("./x.png", false)).isEqualTo("x.png");
        assertThat(DocsEmbedTargetCleaner.clean("/././/x.png", false)).isEqualTo("x.png");
        String unc = DocsEmbedTargetCleaner.clean("//h/s/x.png", false);
        assertThat(unc).isEqualTo("h/s/x.png");
        assertThat(Path.of(unc).isAbsolute()).isFalse();
        assertThat(DocsEmbedTargetCleaner.clean("\\\\h\\s\\x.png", false)).isEqualTo("h/s/x.png");
    }

    @Test
    @DisplayName("does not URL-decode (Spring already decoded the query parameter)")
    void noUrlDecoding() {
        assertThat(DocsEmbedTargetCleaner.clean("My%20Pic.png", false)).isEqualTo("My%20Pic.png");
        assertThat(DocsEmbedTargetCleaner.clean("%2e%2e/x.png", false)).isEqualTo("%2e%2e/x.png");
    }

    @Test
    @DisplayName("keeps .. segments for the resolver's scope checks")
    void keepsDotDot() {
        assertThat(DocsEmbedTargetCleaner.clean("../../x.png", false)).isEqualTo("../../x.png");
    }

    @ParameterizedTest
    @ValueSource(strings = {"C:x.png", "C:\\x.png", "c:/x.png", "Z:/a/b.png", "x\0.png", "", "   ", "|x.png", "/", "./"})
    @DisplayName("drive prefixes, NUL and blank are rejected as not found")
    void rejectsUnsafe(String target) {
        assertThatThrownBy(() -> DocsEmbedTargetCleaner.clean(target, false))
                .isInstanceOf(DocsFileNotFoundException.class);
    }

    @Test
    @DisplayName("null is rejected")
    void rejectsNull() {
        assertThatThrownBy(() -> DocsEmbedTargetCleaner.clean(null, true))
                .isInstanceOf(DocsFileNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"note.md", "board.canvas", "archive.zip", "x.png.exe", "noext", "dir/", "x.png."})
    @DisplayName("non pdf/image targets are 400")
    void rejectsNonBinary(String target) {
        assertThatThrownBy(() -> DocsEmbedTargetCleaner.clean(target, false))
                .isInstanceOf(DocsInvalidFileTypeException.class);
    }

    @Test
    @DisplayName("an alternate data stream suffix is never a pdf/image path")
    void adsRejected() {
        assertThatThrownBy(() -> DocsEmbedTargetCleaner.clean("x.png:ads", true))
                .isInstanceOfAny(DocsFileNotFoundException.class, DocsInvalidFileTypeException.class);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @DisplayName("Windows: \\\\?\\UNC, device paths and stream names are rejected as not found")
    void windowsDevicePaths() {
        assertThatThrownBy(() -> DocsEmbedTargetCleaner.clean("\\\\?\\UNC\\h\\s\\x.png", true))
                .isInstanceOf(DocsFileNotFoundException.class);
        assertThatThrownBy(() -> DocsEmbedTargetCleaner.clean("\\\\?\\C:\\x.png", true))
                .isInstanceOf(DocsFileNotFoundException.class);
        assertThatThrownBy(() -> DocsEmbedTargetCleaner.clean("\\\\.\\C:\\x.png", true))
                .isInstanceOf(DocsFileNotFoundException.class);
        assertThatThrownBy(() -> DocsEmbedTargetCleaner.clean("x.png:ads", true))
                .isInstanceOf(DocsFileNotFoundException.class);
        assertThatThrownBy(() -> DocsEmbedTargetCleaner.clean("a/x.png::$DATA", true))
                .isInstanceOf(DocsFileNotFoundException.class);
    }
}
