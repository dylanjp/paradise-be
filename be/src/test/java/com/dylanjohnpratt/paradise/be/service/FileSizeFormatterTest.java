package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.util.ByteSizes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileSizeFormatterTest {

    @Test
    void format_zeroBytes_returnsZeroB() {
        assertThat(ByteSizes.format(0)).isEqualTo("0 B");
    }

    @Test
    void format_bytesUnderKB_returnsBytesWithB() {
        assertThat(ByteSizes.format(512)).isEqualTo("512 B");
        assertThat(ByteSizes.format(1)).isEqualTo("1 B");
        assertThat(ByteSizes.format(1023)).isEqualTo("1023 B");
    }

    @Test
    void format_exactKB_returnsWholeNumberKB() {
        assertThat(ByteSizes.format(1024)).isEqualTo("1 KB");
        assertThat(ByteSizes.format(5 * 1024)).isEqualTo("5 KB");
    }

    @Test
    void format_fractionalKB_returnsOneDecimalKB() {
        assertThat(ByteSizes.format(1536)).isEqualTo("1.5 KB");
        // 320 KB = 327680 bytes
        assertThat(ByteSizes.format(327_680)).isEqualTo("320 KB");
    }

    @Test
    void format_exactMB_returnsWholeNumberMB() {
        assertThat(ByteSizes.format(1024L * 1024)).isEqualTo("1 MB");
        assertThat(ByteSizes.format(10L * 1024 * 1024)).isEqualTo("10 MB");
    }

    @Test
    void format_fractionalMB_returnsOneDecimalMB() {
        // 1.5 MB = 1572864 bytes
        assertThat(ByteSizes.format(1_572_864)).isEqualTo("1.5 MB");
    }

    @Test
    void format_exactGB_returnsWholeNumberGB() {
        assertThat(ByteSizes.format(1024L * 1024 * 1024)).isEqualTo("1 GB");
    }

    @Test
    void format_fractionalGB_returnsOneDecimalGB() {
        // 2.1 GB ≈ 2254857830 bytes
        long twoPointOneGB = (long) (2.1 * 1024 * 1024 * 1024);
        assertThat(ByteSizes.format(twoPointOneGB)).isEqualTo("2.1 GB");
    }

    @Test
    void format_exactBoundary_KB() {
        // Exactly 1024 bytes = 1 KB boundary
        assertThat(ByteSizes.format(1024)).isEqualTo("1 KB");
    }

    @Test
    void format_exactBoundary_MB() {
        // Exactly 1048576 bytes = 1 MB boundary
        assertThat(ByteSizes.format(1_048_576)).isEqualTo("1 MB");
    }

    @Test
    void format_exactBoundary_GB() {
        // Exactly 1073741824 bytes = 1 GB boundary
        assertThat(ByteSizes.format(1_073_741_824)).isEqualTo("1 GB");
    }
}
