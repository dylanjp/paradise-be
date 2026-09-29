package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.exception.DocsFileNotFoundException;
import com.dylanjohnpratt.paradise.be.exception.DocsInvalidFileTypeException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Cleans the {@code target} of an embed request ({@code ![[target]]}, {@code ![](target)} or a canvas
 * {@code file}) into a vault-relative path. Pure and static so it can be tested on its own.
 * The query parameter is already URL-decoded by Spring, so nothing is decoded here: decoding twice
 * would let {@code %2e%2e} turn into {@code ..}.
 */
public final class DocsEmbedTargetCleaner {

    private static final Pattern DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:");

    private DocsEmbedTargetCleaner() {}

    /**
     * Cleans an embed target.
     * <ol>
     *   <li>Unless {@code literal}, cuts the Obsidian suffixes {@code |alias}, {@code #heading} and {@code ^block}
     *       (and the {@code \} of a table-escaped {@code \|}).
     *       Canvas {@code file} values are literal, so {@code #ClairLineArt.jpg} keeps its {@code #}.</li>
     *   <li>Trims, NFC-normalizes, converts {@code \} to {@code /}, and strips every leading {@code /} and
     *       {@code ./} (Obsidian treats a leading slash as the vault root).</li>
     *   <li>Rejects NUL, a drive prefix ({@code C:}), anything the OS parses as rooted or cannot parse.</li>
     *   <li>Requires a binary (pdf or image) extension.</li>
     * </ol>
     *
     * @param target  the raw target
     * @param literal true to keep {@code | # ^} as part of the file name
     * @return the cleaned relative target, with {@code /} separators and its original case
     * @throws DocsFileNotFoundException    if the target is blank or not a safe relative path
     * @throws DocsInvalidFileTypeException if the target is not a pdf or supported image
     */
    public static String clean(String target, boolean literal) {
        if (target == null) {
            throw notFound();
        }
        String t = target;
        if (!literal) {
            t = cutObsidianSuffix(t);
        }
        t = Normalizer.normalize(t.strip(), Normalizer.Form.NFC).replace('\\', '/');
        while (true) {
            if (t.startsWith("/")) {
                t = t.substring(1);
            } else if (t.startsWith("./")) {
                t = t.substring(2);
            } else {
                break;
            }
        }
        if (t.isEmpty() || t.indexOf('\0') >= 0 || DRIVE_PREFIX.matcher(t).find()) {
            throw notFound();
        }
        try {
            if (Path.of(t).getRoot() != null) {
                throw notFound();
            }
        } catch (InvalidPathException e) {
            throw notFound();
        }
        if (!DocsFileTypes.isBinary(t)) {
            throw new DocsInvalidFileTypeException("Only PDF and image files can be embedded");
        }
        return t;
    }

    /**
     * Cuts the string at the first {@code |}, {@code #} or {@code ^}. A {@code \} right before a cut
     * {@code |} is Obsidian's pipe escape inside tables ({@code ![[pic.png\|300]]}) and is dropped too,
     * as the frontend's {@code parseWikiTarget} does.
     */
    static String cutObsidianSuffix(String target) {
        int cut = target.length();
        for (char c : new char[] {'|', '#', '^'}) {
            int i = target.indexOf(c);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        if (cut > 0 && cut < target.length() && target.charAt(cut) == '|' && target.charAt(cut - 1) == '\\') {
            cut--;
        }
        return target.substring(0, cut);
    }

    private static DocsFileNotFoundException notFound() {
        return new DocsFileNotFoundException("Embedded file not found");
    }
}
