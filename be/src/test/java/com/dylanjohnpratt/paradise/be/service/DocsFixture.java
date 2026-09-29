package com.dylanjohnpratt.paradise.be.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Builds a small copy of the real vault layouts for docs tests.
 * <pre>
 * base/
 *   outside.png                      outside every vault scope
 *   TechVault/                       Obsidian vault (.obsidian) — scope of the main root
 *     Attachments/                   vault-level attachments used by Documentation/ canvases and notes
 *     Documentation/                 DOCS_PATH (main root), with .trash, .rbcf, empty and txt-only folders
 *   LegendaryEpics/                  LE_DOCS_PATH, outer vault with two nested inner vaults
 *     IP Management/Lamaryah WorldBuilding/     inner vault, Amira Board.canvas uses inner-vault paths
 *     Projects/Development/Project SwordBreak/  inner vault, Amira.md uses outer-vault paths, '#name.jpg'
 * </pre>
 * Duplicate base names: {@code shared.png} (Tech), {@code icon.png} and {@code Daken.md} (LE).
 */
public final class DocsFixture {

    /** The Amira note from Project SwordBreak, trimmed to the parts the docs features care about. */
    public static final String AMIRA_SAMPLE = String.join("\n",
            "*Physical Description:*",
            "Amira is 5'7 with green eyes and long blonde hair. Red studded earrings. Biological Age: 22",
            "",
            "![[IP Management/Lamaryah WorldBuilding/Attachments/Pictures/Amira/Official_Amira_Artwork2.jpg]]",
            "",
            "For more art see: [[Amira Artwork]]",
            "",
            "*Weapons:*",
            "[[Faithkeeper]] (Sword), [[Honor's Guard]] (Shield)",
            "",
            "*Character History*",
            "-Amira duels [[IP Management/Lamaryah WorldBuilding/Characters/The 7 Atrocities/Daken/Daken]] at 20",
            "",
            "#Character #Lamaryah #SwordBreak",
            "");

    public static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13};
    public static final byte[] JPG_BYTES = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 16};
    public static final byte[] PDF_BYTES = "%PDF-1.4 fixture".getBytes(StandardCharsets.US_ASCII);
    public static final String SVG_TEXT =
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>window.__pwned=1</script></svg>";

    /** Café in NFC, as stored on disk. */
    public static final String CAFE_NFC = "Café.png";
    /** Café in NFD, as a macOS client might write it in a link. */
    public static final String CAFE_NFD = "Café.png";

    public final Path base;
    public final Path techVault;
    public final Path docs;
    public final Path le;
    public final Path lamaryah;
    public final Path swordBreak;

    private DocsFixture(Path base) {
        this.base = base;
        this.techVault = base.resolve("TechVault");
        this.docs = techVault.resolve("Documentation");
        this.le = base.resolve("LegendaryEpics");
        this.lamaryah = le.resolve("IP Management/Lamaryah WorldBuilding");
        this.swordBreak = le.resolve("Projects/Development/Project SwordBreak");
    }

    /** Creates the fixture under {@code base}. */
    public static DocsFixture create(Path base) throws IOException {
        DocsFixture f = new DocsFixture(base);
        f.write();
        return f;
    }

    private void write() throws IOException {
        bytes(base.resolve("outside.png"), PNG_BYTES);

        // --- Tech vault: vault-level Attachments, Documentation/ is DOCS_PATH ---
        text(techVault.resolve(".obsidian/app.json"), "{}");
        bytes(techVault.resolve(".obsidian/secret.png"), PNG_BYTES);
        bytes(techVault.resolve("Attachments/server.png"), PNG_BYTES);
        bytes(techVault.resolve("Attachments/shared.png"), PNG_BYTES);
        bytes(techVault.resolve("Attachments/unreferenced.png"), PNG_BYTES);
        bytes(techVault.resolve("Attachments/My Pic.png"), PNG_BYTES);
        bytes(techVault.resolve("Attachments/" + CAFE_NFC), PNG_BYTES);
        text(techVault.resolve("Attachments/logo.svg"), SVG_TEXT);
        text(techVault.resolve("Welcome.md"), "# Outside Documentation\n");

        text(docs.resolve("Alpha.md"), "# Alpha\n");
        text(docs.resolve("zeta.md"), "![](../../outside.png)\n");
        text(docs.resolve("README.txt"), "not listed\n");
        text(docs.resolve(".trash/x.md"), "# trashed\n");
        bytes(docs.resolve(".trash/trashed.png"), PNG_BYTES);

        text(docs.resolve("Home/Basement.md"), String.join("\n",
                "# Basement",
                "![[Screenshot 1.png]]",
                "![[shared.png]]",
                "![](../../Attachments/My%20Pic.png)",
                "![[" + CAFE_NFD + "]]",
                "![[secret.png]]",
                "![[trashed.png]]",
                "![](../.trash/trashed.png)",
                "![[Alpha.md]]",
                "![[logo.svg|200]]",
                ""));
        bytes(docs.resolve("Home/Screenshot 1.png"), PNG_BYTES);
        bytes(docs.resolve("Home/shared.png"), PNG_BYTES);
        text(docs.resolve("Home/Network/Network Diagram.canvas"), """
                {"nodes":[
                  {"id":"a","type":"file","file":"Attachments/server.png","x":0,"y":0,"width":100,"height":100},
                  {"id":"b","type":"text","text":"Router ![[shared.png]]","x":200,"y":0,"width":100,"height":100},
                  {"id":"c","type":"file","file":"Attachments/logo.svg","x":400,"y":0,"width":100,"height":100}
                ],"edges":[{"id":"e","fromNode":"a","toNode":"b"}]}
                """);
        Files.createDirectories(docs.resolve("Home/empty-folder"));
        text(docs.resolve("Home/only-txt/notes.txt"), "txt only\n");

        text(docs.resolve("Insurance/Insurance Overview.md"), "# Insurance\n");
        bytes(docs.resolve("Insurance/Car Insurance/ID Card.pdf"), PDF_BYTES);

        bytes(docs.resolve("Sprinklers/Pratt_Tessa_Rainbird.rbcf"), new byte[] {1, 2, 3});
        bytes(docs.resolve("Sprinklers/HowToGuide.pdf"), PDF_BYTES);
        text(docs.resolve("Sprinklers/Rainbird.md"), "# Rainbird\n");

        // --- LegendaryEpics: outer vault with nested inner vaults ---
        text(le.resolve(".obsidian/app.json"), "{}");
        text(le.resolve("Amira Artwork.md"), "");

        text(lamaryah.resolve(".obsidian/app.json"), "{}");
        bytes(lamaryah.resolve("Attachments/Pictures/Amira/Official_Amira_Artwork1.jpg"), JPG_BYTES);
        bytes(lamaryah.resolve("Attachments/Pictures/Amira/Official_Amira_Artwork2.jpg"), JPG_BYTES);
        bytes(lamaryah.resolve("Attachments/icon.png"), PNG_BYTES);
        text(lamaryah.resolve("Characters/Amira/Amira.md"), "![[icon.png]]\n**Weapons:** [[Faithkeeper]]\n");
        text(lamaryah.resolve("Characters/Amira/Amira Board.canvas"), """
                {"nodes":[
                  {"id":"1","type":"file","file":"Attachments/Pictures/Amira/Official_Amira_Artwork1.jpg","x":0,"y":0,"width":400,"height":400},
                  {"id":"2","type":"file","file":"IP Management/Lamaryah WorldBuilding/Characters/Amira/Amira.md","x":500,"y":0,"width":400,"height":400},
                  {"id":"3","type":"text","text":"# [[IP Management/Lamaryah WorldBuilding/Characters/Amira/Amira]]","x":0,"y":500,"width":400,"height":100}
                ],"edges":[]}
                """);
        text(lamaryah.resolve("Characters/The 7 Atrocities/Daken/Daken.md"), "# Daken\n");

        text(swordBreak.resolve(".obsidian/app.json"), "{}");
        bytes(swordBreak.resolve("Attachments/icon.png"), PNG_BYTES);
        bytes(swordBreak.resolve("Attachments/Images/Concept/Characters/Amira/#ClairLineArt.jpg"), JPG_BYTES);
        text(swordBreak.resolve("Concept Board.canvas"), """
                {"nodes":[
                  {"id":"1","type":"file","file":"Attachments/Images/Concept/Characters/Amira/#ClairLineArt.jpg","x":0,"y":0,"width":300,"height":300}
                ],"edges":[]}
                """);
        text(swordBreak.resolve("Worldbuilding/Characters/Main Characters/Amira/Amira.md"),
                AMIRA_SAMPLE
                        + "![[icon.png]]\n"
                        + "![[Pictures/Amira/Official_Amira_Artwork1.jpg]]\n"
                        + "![[Wrong/Official_Amira_Artwork1.jpg]]\n");
        text(swordBreak.resolve("Worldbuilding/Characters/Main Characters/Daken/Daken.md"), "# Daken (SwordBreak)\n");
    }

    /** Tree path of the SwordBreak Amira note inside the "LE Docs" root. */
    public static final String LE_AMIRA_NOTE =
            "LE Docs/Projects/Development/Project SwordBreak/Worldbuilding/Characters/Main Characters/Amira/Amira.md";

    /** Tree path of the Amira Board canvas inside the "LE Docs" root. */
    public static final String LE_AMIRA_BOARD =
            "LE Docs/IP Management/Lamaryah WorldBuilding/Characters/Amira/Amira Board.canvas";

    public static void text(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    public static void bytes(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    /** Deletes a directory tree without following links. */
    public static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
