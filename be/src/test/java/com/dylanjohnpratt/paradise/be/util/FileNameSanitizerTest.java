package com.dylanjohnpratt.paradise.be.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileNameSanitizerTest {

    @Test
    void sanitize_simpleName_returnsUnchanged() {
        assertThat(FileNameSanitizer.sanitize("report.pdf")).isEqualTo("report.pdf");
        assertThat(FileNameSanitizer.sanitize("a b c.txt")).isEqualTo("a b c.txt");
    }

    @Test
    void sanitize_stripsForwardSlash() {
        assertThat(FileNameSanitizer.sanitize("foo/bar.pdf")).isEqualTo("foobar.pdf");
    }

    @Test
    void sanitize_stripsBackslash() {
        assertThat(FileNameSanitizer.sanitize("foo\\bar.pdf")).isEqualTo("foobar.pdf");
    }

    @Test
    void sanitize_stripsBothSeparators() {
        assertThat(FileNameSanitizer.sanitize("a/b\\c/d.pdf")).isEqualTo("abcd.pdf");
    }

    @Test
    void sanitize_stripsControlCharacters() {
        // Embed a NUL, a newline, and 0x7F (DEL)
        String withControls = "file\u0000name\nx\u007F.txt";
        assertThat(FileNameSanitizer.sanitize(withControls)).isEqualTo("filenamex.txt");
    }

    @Test
    void sanitize_preservesUnicode() {
        assertThat(FileNameSanitizer.sanitize("résumé-日本語.pdf"))
                .isEqualTo("résumé-日本語.pdf");
    }

    @Test
    void sanitize_trimsFromFrontWhenTooLong() {
        String longName = "a".repeat(300) + ".pdf";
        String result = FileNameSanitizer.sanitize(longName);
        assertThat(result).hasSize(FileNameSanitizer.MAX_FILENAME_LENGTH);
        // The sanitizer retains the LAST MAX_FILENAME_LENGTH chars — so the ".pdf" extension survives.
        assertThat(result).endsWith(".pdf");
    }

    @Test
    void sanitize_null_throws() {
        assertThatThrownBy(() -> FileNameSanitizer.sanitize(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sanitize_blank_throws() {
        assertThatThrownBy(() -> FileNameSanitizer.sanitize(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FileNameSanitizer.sanitize("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sanitize_onlySeparators_throws() {
        assertThatThrownBy(() -> FileNameSanitizer.sanitize("/\\/\\"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sanitize_onlyControls_throws() {
        assertThatThrownBy(() -> FileNameSanitizer.sanitize("\u0000\u0001\u0002"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
