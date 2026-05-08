package com.dylanjohnpratt.paradise.be.util;

public final class ByteSizes {

    public static final long KB = 1024L;
    public static final long MB = KB * 1024;
    public static final long GB = MB * 1024;

    private ByteSizes() {}

    public static String format(long bytes) {
        if (bytes < KB) {
            return bytes + " B";
        } else if (bytes < MB) {
            return formatUnit(bytes, KB, "KB");
        } else if (bytes < GB) {
            return formatUnit(bytes, MB, "MB");
        } else {
            return formatUnit(bytes, GB, "GB");
        }
    }

    private static String formatUnit(long bytes, long unit, String suffix) {
        double value = (double) bytes / unit;
        if (value == Math.floor(value)) {
            return (long) value + " " + suffix;
        }
        return String.format("%.1f %s", value, suffix);
    }
}
