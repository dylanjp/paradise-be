package com.dylanjohnpratt.paradise.be.util;

public final class FileNameSanitizer {

    public static final int MAX_FILENAME_LENGTH = 255;

    private FileNameSanitizer() {}

    public static String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name is required");
        }
        StringBuilder sb = new StringBuilder(fileName.length());
        for (int i = 0; i < fileName.length(); i++) {
            char c = fileName.charAt(i);
            if (c == '/' || c == '\\' || c < 0x20 || c == 0x7F) {
                continue;
            }
            sb.append(c);
        }
        String sanitized = sb.toString();
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("File name is invalid");
        }
        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            sanitized = sanitized.substring(sanitized.length() - MAX_FILENAME_LENGTH);
        }
        return sanitized;
    }
}
