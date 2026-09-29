package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.config.DocsPathProperties;
import com.dylanjohnpratt.paradise.be.dto.DocsTreeNode;
import com.dylanjohnpratt.paradise.be.exception.DocsFileNotFoundException;
import com.dylanjohnpratt.paradise.be.exception.DocsFileTooLargeException;
import com.dylanjohnpratt.paradise.be.exception.DocsInvalidFileTypeException;
import com.dylanjohnpratt.paradise.be.exception.DocsPathTraversalException;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.service.DocsService.ResolvedFile;
import com.dylanjohnpratt.paradise.be.service.DocsService.TextFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for {@link DocsService} against a temp-dir copy of the real vault layouts
 * (see {@link DocsFixture}): multi-root tree, routing, traversal, types and sizes, embed resolution,
 * and refresh.
 */
class DocsServiceTest {

    @TempDir
    Path tempDir;

    private DocsFixture fx;
    private MutableClock clock;
    private DocsService service;

    private final User admin = user("admin", "ROLE_ADMIN", "ROLE_USER");
    private final User regular = user("tessa", "ROLE_USER");

    @BeforeEach
    void setUp() throws IOException {
        fx = DocsFixture.create(tempDir);
        clock = new MutableClock(Instant.parse("2026-09-28T12:00:00Z"));
        service = service(fx.docs.toString(), fx.le.toString(), "");
    }

    private DocsService service(String docs, String le, String pratt) {
        DocsRoots roots = new DocsRoots(new DocsPathProperties(docs, le, pratt));
        return new DocsService(roots, new DocsCacheManager(), clock);
    }

    private static User user(String name, String... roles) {
        return new User(name, "password", Set.of(roles));
    }

    private static List<String> names(DocsTreeNode node) {
        return node.children().stream().map(DocsTreeNode::name).toList();
    }

    private static DocsTreeNode child(DocsTreeNode node, String name) {
        return node.children().stream()
                .filter(n -> n.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no child " + name + " in " + names(node)));
    }

    private static Optional<DocsTreeNode> find(DocsTreeNode node, String path) {
        if (path.equals(node.path())) {
            return Optional.of(node);
        }
        if (node.children() == null) {
            return Optional.empty();
        }
        for (DocsTreeNode c : node.children()) {
            Optional<DocsTreeNode> hit = find(c, path);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    private static Path real(Path p) throws IOException {
        return p.toRealPath();
    }

    // -----------------------------------------------------------------------
    // Tree
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Tree")
    class Tree {

        @Test
        @DisplayName("admin sees main content plus the LE Docs root folder, folders first then case-insensitive")
        void adminTreeLayoutAndSorting() {
            DocsTreeNode tree = service.getFileTree(admin);

            assertThat(tree.name()).isEmpty();
            assertThat(tree.path()).isEmpty();
            assertThat(tree.type()).isEqualTo("folder");
            assertThat(tree.root()).isNull();
            assertThat(names(tree)).containsExactly("Home", "Insurance", "LE Docs", "Sprinklers", "Alpha.md", "zeta.md");

            DocsTreeNode leDocs = child(tree, "LE Docs");
            assertThat(leDocs.root()).isTrue();
            assertThat(leDocs.path()).isEqualTo("LE Docs");
            assertThat(leDocs.type()).isEqualTo("folder");
            assertThat(child(tree, "Home").root()).isNull();
        }

        @Test
        @DisplayName("extra root children are prefixed with the label")
        void extraRootPathsArePrefixed() {
            DocsTreeNode leDocs = child(service.getFileTree(admin), "LE Docs");

            assertThat(names(leDocs)).containsExactly("IP Management", "Projects", "Amira Artwork.md");
            assertThat(find(leDocs, DocsFixture.LE_AMIRA_NOTE)).isPresent();
            assertThat(find(leDocs, DocsFixture.LE_AMIRA_BOARD)).get()
                    .satisfies(n -> {
                        assertThat(n.type()).isEqualTo("file");
                        assertThat(n.children()).isNull();
                        assertThat(n.root()).isNull();
                    });
            assertThat(find(leDocs, "LE Docs/Projects/Development/Project SwordBreak/Attachments/Images/Concept/Characters/Amira/#ClairLineArt.jpg"))
                    .isPresent();
        }

        @Test
        @DisplayName("empty and txt-only folders are pruned; only md, canvas, pdf and images are listed")
        void pruningAndExtensionFilter() {
            DocsTreeNode tree = service.getFileTree(admin);

            DocsTreeNode home = child(tree, "Home");
            assertThat(names(home)).containsExactly("Network", "Basement.md", "Screenshot 1.png", "shared.png");
            assertThat(names(child(home, "Network"))).containsExactly("Network Diagram.canvas");
            assertThat(find(tree, "Home/Network/Network Diagram.canvas")).isPresent();
            assertThat(names(child(tree, "Sprinklers"))).containsExactly("HowToGuide.pdf", "Rainbird.md");
            assertThat(find(tree, "README.txt")).isEmpty();
            assertThat(find(tree, "Insurance/Car Insurance/ID Card.pdf")).isPresent();
        }

        @Test
        @DisplayName("dot-entries (.obsidian, .trash) are never listed")
        void dotEntriesHidden() {
            DocsTreeNode tree = service.getFileTree(admin);

            assertThat(find(tree, ".trash")).isEmpty();
            assertThat(find(tree, ".trash/x.md")).isEmpty();
            assertThat(find(tree, "LE Docs/.obsidian")).isEmpty();
            assertThat(find(tree, "LE Docs/IP Management/Lamaryah WorldBuilding/.obsidian")).isEmpty();
        }

        @Test
        @DisplayName("a non-admin does not see the LE Docs root")
        void nonAdminFiltered() {
            DocsTreeNode tree = service.getFileTree(regular);

            assertThat(names(tree)).containsExactly("Home", "Insurance", "Sprinklers", "Alpha.md", "zeta.md");
            assertThat(service.getFileTree(null).children()).extracting(DocsTreeNode::name).doesNotContain("LE Docs");
        }

        @Test
        @DisplayName("missing and blank roots are skipped")
        void missingAndBlankRoots() {
            DocsService missingPratt = service(fx.docs.toString(), fx.le.toString(), tempDir.resolve("missing").toString());
            assertThat(names(missingPratt.getFileTree(admin))).doesNotContain("Pratt Capitol");
            assertThatThrownBy(() -> missingPratt.resolveTreePath("Pratt Capitol/x.md", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);

            DocsService onlyLe = service("", fx.le.toString(), "  ");
            assertThat(names(onlyLe.getFileTree(admin))).containsExactly("LE Docs");
            assertThat(onlyLe.getFileTree(regular).children()).isEmpty();
            assertThatThrownBy(() -> onlyLe.resolveTreePath("Alpha.md", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);

            DocsService none = service(null, null, null);
            assertThat(none.getFileTree(admin).children()).isEmpty();
        }

        @Test
        @DisplayName("a DOCS_PATH folder named like a configured vault root is shadowed and not listed")
        void shadowedMainFolderDropped() throws IOException {
            DocsFixture.text(fx.docs.resolve("LE Docs/shadow.md"), "# shadow\n");
            DocsTreeNode tree = service(fx.docs.toString(), fx.le.toString(), "").getFileTree(admin);

            assertThat(tree.children()).filteredOn(n -> n.name().equals("LE Docs")).hasSize(1)
                    .allSatisfy(n -> assertThat(n.root()).isTrue());
            assertThat(find(tree, "LE Docs/shadow.md")).isEmpty();
        }

        @Test
        @DisplayName("an admin-only vault nested inside DOCS_PATH is not listed under the open root")
        void nestedAdminVaultNotListedInMain() {
            DocsService nested = service(fx.base.toString(), fx.le.toString(), "");

            DocsTreeNode userTree = nested.getFileTree(regular);
            assertThat(names(userTree)).doesNotContain("LegendaryEpics", "LE Docs");
            assertThatThrownBy(() -> nested.resolveTreePath("LegendaryEpics/Amira Artwork.md", regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }
    }

    // -----------------------------------------------------------------------
    // Routing and traversal
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Routing and traversal")
    class Routing {

        @Test
        @DisplayName("tree paths resolve to real files in their root")
        void resolvesTreePaths() throws IOException {
            assertThat(service.resolveTreePath("Home/Basement.md", regular).path())
                    .isEqualTo(real(fx.docs.resolve("Home/Basement.md")));
            ResolvedFile le = service.resolveTreePath(DocsFixture.LE_AMIRA_BOARD, admin);
            assertThat(le.path()).isEqualTo(real(fx.lamaryah.resolve("Characters/Amira/Amira Board.canvas")));
            assertThat(le.root().label()).isEqualTo("LE Docs");
            assertThat(le.scopeRelativePath())
                    .isEqualTo("IP Management/Lamaryah WorldBuilding/Characters/Amira/Amira Board.canvas");
        }

        @Test
        @DisplayName("../ escapes are 403, including through a vault root")
        void traversalIs403() {
            assertThatThrownBy(() -> service.resolveTreePath("../Welcome.md", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath("Home/../../Welcome.md", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath("LE Docs/../../x", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath("LE Docs/../TechVault/Welcome.md", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
        }

        @Test
        @DisplayName("dot-entries are 404 even when reached through ..")
        void hiddenIs404() {
            assertThatThrownBy(() -> service.resolveTreePath(".obsidian/app.json", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath("LE Docs/.obsidian/app.json", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath(".trash/x.md", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath("Insurance/../.trash/x.md", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath("Insurance\\..\\.trash\\x.md", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("absolute, rooted, drive-prefixed, NUL and blank inputs are rejected")
        void absoluteAndMalformedRejected() {
            String absolute = fx.docs.resolve("Alpha.md").toAbsolutePath().toString();
            assertThatThrownBy(() -> service.resolveTreePath(absolute, admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath("/etc/passwd.md", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath("\\\\server\\share\\x.md", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath("C:x.md", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath("LE Docs//etc/passwd.md", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath("LE Docs/C:/x.md", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath("Alpha\0.md", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath("", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath("LE Docs", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath("LE Docs/", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @EnabledOnOs(OS.WINDOWS)
        @DisplayName("Windows: alternate data streams and illegal names are 404, not a validation error")
        void windowsInvalidPathsAre404() {
            assertThatThrownBy(() -> service.resolveTreePath("Alpha.md:secret.md", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath("Home/a<b>.md", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("a non-admin gets 404 for anything under LE Docs, existing or not")
        void nonAdminLePathIs404() {
            assertThatThrownBy(() -> service.resolveTreePath("LE Docs/Amira Artwork.md", regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath("LE Docs/nope.md", regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.getTextFile(DocsFixture.LE_AMIRA_NOTE, null))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }
    }

    // -----------------------------------------------------------------------
    // Types and sizes
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Types and sizes")
    class Types {

        @Test
        @DisplayName("md is text/markdown;charset=UTF-8 and canvas is application/json")
        void textMediaTypes() {
            TextFile md = service.getTextFile("Home/Basement.md", regular);
            assertThat(md.content()).startsWith("# Basement");
            assertThat(md.mediaType().toString()).isEqualTo("text/markdown;charset=UTF-8");

            TextFile canvas = service.getTextFile(DocsFixture.LE_AMIRA_BOARD, admin);
            assertThat(canvas.content()).contains("Official_Amira_Artwork1.jpg");
            assertThat(canvas.mediaType().toString()).isEqualTo("application/json");
        }

        @Test
        @DisplayName("invalid UTF-8 is decoded leniently and a BOM is dropped")
        void lenientUtf8() throws IOException {
            DocsFixture.bytes(fx.docs.resolve("bad.md"), new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i', (byte) 0xC3});
            assertThat(service.getTextFile("bad.md", regular).content()).isEqualTo("hi\uFFFD");
        }

        @Test
        @DisplayName("wrong type for the endpoint is 400")
        void wrongTypeIs400() {
            assertThatThrownBy(() -> service.getTextFile("Insurance/Car Insurance/ID Card.pdf", regular))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
            assertThatThrownBy(() -> service.getTextFile("README.txt", regular))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
            assertThatThrownBy(() -> service.getBinaryFile("Home/Basement.md", regular))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
            assertThatThrownBy(() -> service.getBinaryFile("Sprinklers/Pratt_Tessa_Rainbird.rbcf", regular))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
            assertThatThrownBy(() -> service.getTextFile("Alpha.md.", regular))
                    .isInstanceOfAny(DocsInvalidFileTypeException.class, DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("binary files resolve for pdf and images, case-insensitively by extension")
        void binaryResolves() throws IOException {
            assertThat(service.getBinaryFile("Insurance/Car Insurance/ID Card.pdf", regular).path())
                    .isEqualTo(real(fx.docs.resolve("Insurance/Car Insurance/ID Card.pdf")));
            DocsFixture.bytes(fx.docs.resolve("Home/UPPER.PNG"), DocsFixture.PNG_BYTES);
            assertThat(service.getBinaryFile("Home/UPPER.PNG", regular).fileName()).isEqualTo("UPPER.PNG");
        }

        @Test
        @DisplayName("text files over 32 MB are 413")
        void tooLargeIs413() throws IOException {
            Path big = fx.docs.resolve("Big/huge.md");
            Files.createDirectories(big.getParent());
            try (RandomAccessFile raf = new RandomAccessFile(big.toFile(), "rw")) {
                raf.setLength(DocsService.MAX_TEXT_BYTES + 1);
            }
            assertThatThrownBy(() -> service.getTextFile("Big/huge.md", regular))
                    .isInstanceOf(DocsFileTooLargeException.class);
        }
    }

    // -----------------------------------------------------------------------
    // Embeds
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Embeds")
    class Embeds {

        @Test
        @DisplayName("Amira Board: inner-vault-relative canvas file resolves in the nested vault")
        void amiraBoardInnerVault() throws IOException {
            ResolvedFile f = service.resolveEmbed(DocsFixture.LE_AMIRA_BOARD,
                    "Attachments/Pictures/Amira/Official_Amira_Artwork1.jpg", true, admin);
            assertThat(f.path()).isEqualTo(real(fx.lamaryah.resolve("Attachments/Pictures/Amira/Official_Amira_Artwork1.jpg")));
            assertThat(f.scopeRelativePath())
                    .isEqualTo("IP Management/Lamaryah WorldBuilding/Attachments/Pictures/Amira/Official_Amira_Artwork1.jpg");
        }

        @Test
        @DisplayName("Amira note: outer-vault-relative embed from inside a nested vault")
        void amiraNoteOuterVault() throws IOException {
            ResolvedFile f = service.resolveEmbed(DocsFixture.LE_AMIRA_NOTE,
                    "IP Management/Lamaryah WorldBuilding/Attachments/Pictures/Amira/Official_Amira_Artwork2.jpg",
                    false, admin);
            assertThat(f.path()).isEqualTo(real(fx.lamaryah.resolve("Attachments/Pictures/Amira/Official_Amira_Artwork2.jpg")));
        }

        @Test
        @DisplayName("Tech vault: Documentation/ canvas reaches the vault-level Attachments/")
        void techVaultAttachmentsFromDocumentation() throws IOException {
            ResolvedFile f = service.resolveEmbed("Home/Network/Network Diagram.canvas",
                    "Attachments/server.png", true, regular);
            assertThat(f.path()).isEqualTo(real(fx.techVault.resolve("Attachments/server.png")));
            assertThat(f.scopeRelativePath()).isEqualTo("Attachments/server.png");
            assertThat(f.root().isMain()).isTrue();
        }

        @Test
        @DisplayName("duplicate names resolve to the closest file")
        void duplicatesByProximity() throws IOException {
            // Ancestor walk: Home/shared.png beats TechVault/Attachments/shared.png
            assertThat(service.resolveEmbed("Home/Basement.md", "shared.png", false, regular).path())
                    .isEqualTo(real(fx.docs.resolve("Home/shared.png")));
            assertThat(service.resolveEmbed("Home/Network/Network Diagram.canvas", "shared.png", false, regular).path())
                    .isEqualTo(real(fx.docs.resolve("Home/shared.png")));
            // Name index: each note gets the icon.png of its own inner vault
            assertThat(service.resolveEmbed(DocsFixture.LE_AMIRA_NOTE, "icon.png", false, admin).path())
                    .isEqualTo(real(fx.swordBreak.resolve("Attachments/icon.png")));
            assertThat(service.resolveEmbed("LE Docs/IP Management/Lamaryah WorldBuilding/Characters/Amira/Amira.md",
                    "icon.png", false, admin).path())
                    .isEqualTo(real(fx.lamaryah.resolve("Attachments/icon.png")));
        }

        @Test
        @DisplayName("a target with folders matches by path suffix in the name index")
        void suffixMatch() throws IOException {
            assertThat(service.resolveEmbed(DocsFixture.LE_AMIRA_NOTE,
                    "Pictures/Amira/Official_Amira_Artwork1.jpg", false, admin).path())
                    .isEqualTo(real(fx.lamaryah.resolve("Attachments/Pictures/Amira/Official_Amira_Artwork1.jpg")));
            assertThatThrownBy(() -> service.resolveEmbed(DocsFixture.LE_AMIRA_NOTE,
                    "Wrong/Official_Amira_Artwork1.jpg", false, admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("case and Unicode normalization variants resolve")
        void caseAndNfcVariants() throws IOException {
            Path expected = real(fx.techVault.resolve("Attachments/" + DocsFixture.CAFE_NFC));
            assertThat(service.resolveEmbed("Home/Basement.md", DocsFixture.CAFE_NFD, false, regular).path())
                    .isEqualTo(expected);
            assertThat(service.resolveEmbed("Home/Basement.md", "CAF\u00c9.PNG", false, regular).path())
                    .isEqualTo(expected);
            assertThat(service.resolveEmbed("Home/Basement.md", "screenshot 1.PNG", false, regular).path())
                    .isEqualTo(real(fx.docs.resolve("Home/Screenshot 1.png")));
        }

        @Test
        @DisplayName("URL-decoded relative markdown image resolves; the raw form is not decoded again")
        void relativeMarkdownImage() throws IOException {
            assertThat(service.resolveEmbed("Home/Basement.md", "../../Attachments/My Pic.png", false, regular).path())
                    .isEqualTo(real(fx.techVault.resolve("Attachments/My Pic.png")));
            assertThatThrownBy(() -> service.resolveEmbed("Home/Basement.md", "../../Attachments/My%20Pic.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("literal '#name.jpg' from a canvas resolves; non-literal strips it to nothing")
        void literalHashName() throws IOException {
            String target = "Attachments/Images/Concept/Characters/Amira/#ClairLineArt.jpg";
            assertThat(service.resolveEmbed("LE Docs/Projects/Development/Project SwordBreak/Concept Board.canvas",
                    target, true, admin).path())
                    .isEqualTo(real(fx.swordBreak.resolve(target)));
            assertThatThrownBy(() -> service.resolveEmbed(
                    "LE Docs/Projects/Development/Project SwordBreak/Concept Board.canvas", target, false, admin))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
        }

        @Test
        @DisplayName("alias and size suffixes are stripped from Obsidian targets")
        void aliasStripped() throws IOException {
            assertThat(service.resolveEmbed("Home/Basement.md", "logo.svg|200", false, regular).path())
                    .isEqualTo(real(fx.techVault.resolve("Attachments/logo.svg")));
        }

        @Test
        @DisplayName("a target the note does not reference is 404 (reference gate)")
        void unreferencedIs404() {
            assertThatThrownBy(() -> service.resolveEmbed("Home/Basement.md", "unreferenced.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveEmbed("Alpha.md", "server.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("a note target (.md) is 400")
        void mdTargetIs400() {
            assertThatThrownBy(() -> service.resolveEmbed("Home/Basement.md", "Alpha.md", false, regular))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
        }

        @Test
        @DisplayName("../../x.png leaving the vault scope is 404 even when referenced")
        void escapingScopeIs404() {
            assertThatThrownBy(() -> service.resolveEmbed("zeta.md", "../../outside.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("files in dot-folders are never served, by path or by name")
        void hiddenNeverServed() {
            assertThatThrownBy(() -> service.resolveEmbed("Home/Basement.md", "secret.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveEmbed("Home/Basement.md", "trashed.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveEmbed("Home/Basement.md", "../.trash/trashed.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("from must be a readable text doc; non-admins cannot embed from LE Docs")
        void fromChecks() {
            assertThatThrownBy(() -> service.resolveEmbed(DocsFixture.LE_AMIRA_BOARD,
                    "Attachments/Pictures/Amira/Official_Amira_Artwork1.jpg", true, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveEmbed("Insurance/Car Insurance/ID Card.pdf", "x.png", false, regular))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
            assertThatThrownBy(() -> service.resolveEmbed("../Welcome.md", "server.png", false, regular))
                    .isInstanceOf(DocsPathTraversalException.class);
        }

        @Test
        @DisplayName("Obsidian's table-escaped pipe '![[pic.png\\|300]]' resolves pic.png (the frontend sends 'pic.png')")
        void escapedPipeTableEmbed() throws IOException {
            DocsFixture.text(fx.docs.resolve("Gallery.md"), String.join("\n",
                    "| Picture | Width |",
                    "| --- | --- |",
                    "| ![[pic.png\\|300]] | 300 |",
                    ""));
            DocsFixture.bytes(fx.docs.resolve("pic.png"), DocsFixture.PNG_BYTES);
            Path expected = real(fx.docs.resolve("pic.png"));

            assertThat(service.resolveEmbed("Gallery.md", "pic.png", false, regular).path()).isEqualTo(expected);
            assertThat(service.resolveEmbed("Gallery.md", "pic.png", false, admin).path()).isEqualTo(expected);
            assertThat(service.resolveEmbed("Gallery.md", "pic.png\\|300", false, regular).path()).isEqualTo(expected);
        }

        @Test
        @DisplayName("a reference scan that overflows the stack is a 404, cached so the note is not re-scanned")
        void referenceScanOverflowIsCached404() {
            AtomicInteger scans = new AtomicInteger();
            DocsService overflowing = new DocsService(
                    new DocsRoots(new DocsPathProperties(fx.docs.toString(), fx.le.toString(), "")),
                    new DocsCacheManager(), clock, (content, canvas) -> {
                        scans.incrementAndGet();
                        throw new StackOverflowError("simulated pathological note");
                    });

            for (int i = 0; i < 3; i++) {
                assertThatThrownBy(() -> overflowing.resolveEmbed("Home/Basement.md", "shared.png", false, regular))
                        .isInstanceOf(DocsFileNotFoundException.class);
            }
            assertThat(scans).as("scanned once per file version").hasValue(1);
        }

        @Test
        @DisplayName("an edited note updates its reference set")
        void referenceCacheInvalidatedOnChange() throws IOException {
            assertThatThrownBy(() -> service.resolveEmbed("Alpha.md", "server.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            DocsFixture.text(fx.docs.resolve("Alpha.md"), "# Alpha\n![[server.png]]\n");
            Files.setLastModifiedTime(fx.docs.resolve("Alpha.md"),
                    java.nio.file.attribute.FileTime.from(Instant.parse("2030-01-01T00:00:00Z")));
            assertThat(service.resolveEmbed("Alpha.md", "server.png", false, regular).path())
                    .isEqualTo(real(fx.techVault.resolve("Attachments/server.png")));
        }
    }

    // -----------------------------------------------------------------------
    // Refresh
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Refresh")
    class Refresh {

        @Test
        @DisplayName("refresh picks up new files once the 10 s throttle has passed")
        void refreshPicksUpNewFilesAfterThrottle() throws IOException {
            assertThat(find(service.getFileTree(admin), "New.md")).isEmpty();
            DocsFixture.text(fx.docs.resolve("New.md"), "# new\n");

            clock.advance(Duration.ofSeconds(5));
            service.refresh();
            assertThat(find(service.getFileTree(admin), "New.md")).as("throttled").isEmpty();

            clock.advance(Duration.ofSeconds(6));
            service.refresh();
            assertThat(find(service.getFileTree(admin), "New.md")).isPresent();
        }

        @Test
        @DisplayName("a wall clock stepped backwards does not suppress refreshes")
        void clockSteppedBackwardsStillRefreshes() throws IOException {
            assertThat(find(service.getFileTree(admin), "New.md")).isEmpty();
            DocsFixture.text(fx.docs.resolve("New.md"), "# new\n");

            clock.advance(Duration.ofHours(-2));
            service.refresh();
            assertThat(find(service.getFileTree(admin), "New.md")).as("refreshed after the step back").isPresent();

            // The throttle restarts from the rebuilt snapshot's time.
            DocsFixture.text(fx.docs.resolve("Newer.md"), "# newer\n");
            clock.advance(Duration.ofSeconds(5));
            service.refresh();
            assertThat(find(service.getFileTree(admin), "Newer.md")).as("throttled").isEmpty();
        }

        @Test
        @DisplayName("refresh re-checks roots: a root that appears later becomes active")
        void rootAppearsLater() throws IOException {
            Path later = tempDir.resolve("Later");
            DocsService s = service(fx.docs.toString(), fx.le.toString(), later.toString());
            assertThat(names(s.getFileTree(admin))).doesNotContain("Pratt Capitol");

            DocsFixture.text(later.resolve("Capitol.md"), "# capitol\n");
            clock.advance(Duration.ofSeconds(11));
            s.refresh();
            DocsTreeNode tree = s.getFileTree(admin);
            assertThat(child(tree, "Pratt Capitol").root()).isTrue();
            assertThat(find(tree, "Pratt Capitol/Capitol.md")).isPresent();
            assertThat(names(s.getFileTree(regular))).doesNotContain("Pratt Capitol");
        }

        @Test
        @DisplayName("new embed targets are found by name after a refresh")
        void embedIndexRefreshed() throws IOException {
            DocsFixture.text(fx.docs.resolve("Alpha.md"), "![[late.png]]\n");
            Files.setLastModifiedTime(fx.docs.resolve("Alpha.md"),
                    java.nio.file.attribute.FileTime.from(Instant.parse("2030-01-01T00:00:00Z")));
            service.getFileTree(admin);
            DocsFixture.bytes(fx.techVault.resolve("Attachments/Deep/late.png"), DocsFixture.PNG_BYTES);
            assertThatThrownBy(() -> service.resolveEmbed("Alpha.md", "late.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);

            clock.advance(Duration.ofSeconds(11));
            service.refresh();
            assertThat(service.resolveEmbed("Alpha.md", "late.png", false, regular).path())
                    .isEqualTo(real(fx.techVault.resolve("Attachments/Deep/late.png")));
        }
    }

    // -----------------------------------------------------------------------
    // Junctions (Windows) and symlinks
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Links")
    class Links {

        @Test
        @EnabledOnOs(OS.WINDOWS)
        @DisplayName("Windows junctions: loops terminate, targets outside the root are not listed or served")
        void junctions() throws Exception {
            Path loop = fx.docs.resolve("Home/loop");
            Path out = fx.docs.resolve("Home/out");
            assumeTrue(mklinkJunction(loop, fx.docs), "could not create a junction");
            try {
                assumeTrue(mklinkJunction(out, fx.techVault.resolve("Attachments")), "could not create a junction");
                clock.advance(Duration.ofSeconds(11));
                DocsTreeNode tree = service.getFileTree(admin);

                assertThat(find(tree, "Home/out/server.png")).isEmpty();
                assertThat(find(tree, "Home/loop/Home/loop")).isEmpty();
                assertThatThrownBy(() -> service.getBinaryFile("Home/out/server.png", admin))
                        .isInstanceOf(DocsFileNotFoundException.class);
                // The embed index walked the vault without looping and still resolves by name.
                assertThat(service.resolveEmbed("Home/Network/Network Diagram.canvas", "Attachments/server.png", true, admin).path())
                        .isEqualTo(real(fx.techVault.resolve("Attachments/server.png")));
            } finally {
                Files.deleteIfExists(out);
                Files.deleteIfExists(loop);
            }
        }

        @Test
        @DisplayName("symlinks leaving the root are not listed or served")
        void symlinks() throws IOException {
            Path link = fx.docs.resolve("Home/link.png");
            Path dirLink = fx.docs.resolve("Home/dirlink");
            try {
                Files.createSymbolicLink(link, fx.base.resolve("outside.png"));
                Files.createSymbolicLink(dirLink, fx.techVault.resolve("Attachments"));
            } catch (IOException | UnsupportedOperationException | SecurityException e) {
                assumeTrue(false, "symlinks not available: " + e.getMessage());
            }
            try {
                DocsTreeNode tree = service.getFileTree(admin);
                assertThat(find(tree, "Home/link.png")).isEmpty();
                assertThat(find(tree, "Home/dirlink/server.png")).isEmpty();
                assertThatThrownBy(() -> service.getBinaryFile("Home/link.png", admin))
                        .isInstanceOf(DocsFileNotFoundException.class);
                assertThatThrownBy(() -> service.getBinaryFile("Home/dirlink/server.png", admin))
                        .isInstanceOf(DocsFileNotFoundException.class);
            } finally {
                Files.deleteIfExists(link);
                Files.deleteIfExists(dirLink);
            }
        }

        private boolean mklinkJunction(Path link, Path target) throws Exception {
            Process p = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().readAllBytes();
            return p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0 && Files.isDirectory(link);
        }
    }

    /** A clock the tests move by hand. */
    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
