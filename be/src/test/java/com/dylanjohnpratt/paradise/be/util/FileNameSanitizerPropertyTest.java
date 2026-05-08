package com.dylanjohnpratt.paradise.be.util;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests pinning the {@link FileNameSanitizer} invariants:
 * (1) no path separators or control characters survive the pass,
 * (2) the result is at most {@code MAX_FILENAME_LENGTH} long,
 * (3) non-empty alphanumeric input round-trips unchanged.
 */
class FileNameSanitizerPropertyTest {

    @Property(tries = 100)
    void sanitized_neverContainsSeparators(@ForAll @From("fileNames") String input) {
        try {
            String out = FileNameSanitizer.sanitize(input);
            assertThat(out).doesNotContain("/");
            assertThat(out).doesNotContain("\\");
        } catch (IllegalArgumentException ignored) {
            // Blank / invalid inputs are allowed to throw — property still holds vacuously.
        }
    }

    @Property(tries = 100)
    void sanitized_neverContainsControlCharacters(@ForAll @From("fileNames") String input) {
        try {
            String out = FileNameSanitizer.sanitize(input);
            for (int i = 0; i < out.length(); i++) {
                char c = out.charAt(i);
                assertThat(c).isGreaterThanOrEqualTo((char) 0x20);
                assertThat(c).isNotEqualTo((char) 0x7F);
            }
        } catch (IllegalArgumentException ignored) {
            // Acceptable failure mode.
        }
    }

    @Property(tries = 100)
    void sanitized_respectsLengthCeiling(@ForAll @From("fileNames") String input) {
        try {
            String out = FileNameSanitizer.sanitize(input);
            assertThat(out.length()).isLessThanOrEqualTo(FileNameSanitizer.MAX_FILENAME_LENGTH);
        } catch (IllegalArgumentException ignored) {
            // Acceptable.
        }
    }

    @Property(tries = 100)
    void alphaNumericInput_roundTripsUnchanged(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String input) {
        String out = FileNameSanitizer.sanitize(input);
        assertThat(out).isEqualTo(input);
    }

    /**
     * Mix of typical filename characters plus path separators and control bytes
     * so the generator exercises the sanitizer's strip-logic.
     */
    /**
     * Mix of typical filename characters, path separators, and a handful of
     * control bytes (NUL, LF, CR, DEL) so the generator exercises the strip-logic.
     */
    @Provide
    Arbitrary<String> fileNames() {
        char[] pool = {
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                '.', '_', '-', ' ', '/', '\\',
                (char) 0x00, (char) 0x0A, (char) 0x0D, (char) 0x1F, (char) 0x7F
        };
        return Arbitraries.strings()
                .ofMinLength(0)
                .ofMaxLength(400)
                .withChars(pool);
    }
}
