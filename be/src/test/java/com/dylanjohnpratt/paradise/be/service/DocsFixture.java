package com.dylanjohnpratt.paradise.be.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * Builds a small copy of the consolidated Obsidian vault for docs tests.
 * <pre>
 * base/
 *   outside.png                      outside the vault scope
 *   Vault/                           Obsidian vault (.obsidian): DOCS_VAULT_PATH, the scope of every section
 *     Attachments/                   vault-level attachments used by notes and canvases in every section
 *     Welcome.md                     vault root, outside every section
 *     Home and Family/               open section, with .trash, .rbcf, empty and txt-only folders
 *     LLCs/                          admin-only section
 *       LegendaryEpics/              former vault with two nested former vaults (leftover .obsidian folders)
 *         IP Management/Lamaryah WorldBuilding/     Amira Board.canvas uses inner-folder paths
 *         Projects/Development/Project SwordBreak/  Amira.md uses outer-folder paths, '#name.jpg'
 *     Dylan Personal/                admin-only section, with an image that an open note also embeds
 * </pre>
 * Duplicate base names: {@code shared.png} (Attachments and Home), {@code icon.png} and {@code Daken.md} (LLCs).
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

    /** Open section: shown to every signed-in user. */
    public static final String HOME = "Home and Family";
    /** Admin-only section holding the former LegendaryEpics vault. */
    public static final String LLCS = "LLCs";
    /** Admin-only section. */
    public static final String PERSONAL = "Dylan Personal";

    public final Path base;
    public final Path vault;
    public final Path home;
    public final Path llcs;
    public final Path le;
    public final Path lamaryah;
    public final Path swordBreak;
    public final Path personal;

    private DocsFixture(Path base) {
        this.base = base;
        this.vault = base.resolve("Vault");
        this.home = vault.resolve(HOME);
        this.llcs = vault.resolve(LLCS);
        this.le = llcs.resolve("LegendaryEpics");
        this.lamaryah = le.resolve("IP Management/Lamaryah WorldBuilding");
        this.swordBreak = le.resolve("Projects/Development/Project SwordBreak");
        this.personal = vault.resolve(PERSONAL);
    }

    /** Creates the fixture under {@code base}. */
    public static DocsFixture create(Path base) throws IOException {
        DocsFixture f = new DocsFixture(base);
        f.write();
        return f;
    }

    /** The production layout: "Home and Family" open, "Dylan Personal" and "LLCs" admin-only. */
    public DocsRoots roots() {
        return new DocsRoots(vault.toString(), List.of(HOME), List.of(PERSONAL, LLCS));
    }

    /** Tree path of a file in the open section, e.g. {@code h("Home/Basement.md")}. */
    public static String h(String rest) {
        return HOME + "/" + rest;
    }

    private void write() throws IOException {
        bytes(base.resolve("outside.png"), PNG_BYTES);

        // --- Vault: .obsidian, vault-level Attachments, a note outside every section ---
        text(vault.resolve(".obsidian/app.json"), "{}");
        bytes(vault.resolve(".obsidian/secret.png"), PNG_BYTES);
        bytes(vault.resolve("Attachments/server.png"), PNG_BYTES);
        bytes(vault.resolve("Attachments/shared.png"), PNG_BYTES);
        bytes(vault.resolve("Attachments/unreferenced.png"), PNG_BYTES);
        bytes(vault.resolve("Attachments/My Pic.png"), PNG_BYTES);
        bytes(vault.resolve("Attachments/" + CAFE_NFC), PNG_BYTES);
        text(vault.resolve("Attachments/logo.svg"), SVG_TEXT);
        text(vault.resolve("Welcome.md"), "# Outside every section\n");

        // --- Home and Family: open section ---
        text(home.resolve("Alpha.md"), "# Alpha\n");
        text(home.resolve("zeta.md"), "![](../../outside.png)\n");
        text(home.resolve("README.txt"), "not listed\n");
        text(home.resolve(".trash/x.md"), "# trashed\n");
        bytes(home.resolve(".trash/trashed.png"), PNG_BYTES);

        text(home.resolve("Home/Basement.md"), String.join("\n",
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
                "![[private.png]]",
                "![](../../Dylan%20Personal/Accounts/private.png)",
                ""));
        bytes(home.resolve("Home/Screenshot 1.png"), PNG_BYTES);
        bytes(home.resolve("Home/shared.png"), PNG_BYTES);
        text(home.resolve("Home/Network/Network Diagram.canvas"), """
                {"nodes":[
                  {"id":"a","type":"file","file":"Attachments/server.png","x":0,"y":0,"width":100,"height":100},
                  {"id":"b","type":"text","text":"Router ![[shared.png]]","x":200,"y":0,"width":100,"height":100},
                  {"id":"c","type":"file","file":"Attachments/logo.svg","x":400,"y":0,"width":100,"height":100}
                ],"edges":[{"id":"e","fromNode":"a","toNode":"b"}]}
                """);
        Files.createDirectories(home.resolve("Home/empty-folder"));
        text(home.resolve("Home/only-txt/notes.txt"), "txt only\n");

        text(home.resolve("Insurance/Insurance Overview.md"), "# Insurance\n");
        bytes(home.resolve("Insurance/Car Insurance/ID Card.pdf"), PDF_BYTES);

        bytes(home.resolve("Sprinklers/Pratt_Tessa_Rainbird.rbcf"), new byte[] {1, 2, 3});
        bytes(home.resolve("Sprinklers/HowToGuide.pdf"), PDF_BYTES);
        text(home.resolve("Sprinklers/Rainbird.md"), "# Rainbird\n");

        // --- Dylan Personal: admin-only section ---
        text(personal.resolve("Accounts/Accounts.md"), "# Accounts\n![[private.png]]\n");
        bytes(personal.resolve("Accounts/private.png"), PNG_BYTES);

        // --- LLCs/LegendaryEpics: former vault with nested former vaults (admin-only section) ---
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

    /** Tree path prefix of the former LegendaryEpics vault inside the "LLCs" section. */
    public static final String LE = LLCS + "/LegendaryEpics";

    /** Tree path of the SwordBreak Amira note. */
    public static final String LE_AMIRA_NOTE =
            LE + "/Projects/Development/Project SwordBreak/Worldbuilding/Characters/Main Characters/Amira/Amira.md";

    /** Tree path of the Amira Board canvas. */
    public static final String LE_AMIRA_BOARD =
            LE + "/IP Management/Lamaryah WorldBuilding/Characters/Amira/Amira Board.canvas";

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
