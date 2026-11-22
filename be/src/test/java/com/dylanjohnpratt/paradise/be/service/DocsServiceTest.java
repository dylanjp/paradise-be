package com.dylanjohnpratt.paradise.be.service;

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

import static com.dylanjohnpratt.paradise.be.service.DocsFixture.HOME;
import static com.dylanjohnpratt.paradise.be.service.DocsFixture.LE;
import static com.dylanjohnpratt.paradise.be.service.DocsFixture.LLCS;
import static com.dylanjohnpratt.paradise.be.service.DocsFixture.PERSONAL;
import static com.dylanjohnpratt.paradise.be.service.DocsFixture.h;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for {@link DocsService} against a temp-dir copy of the consolidated vault (see
 * {@link DocsFixture}): sections and per-role filtering, routing, traversal, types and sizes, embed
 * resolution across the shared vault scope, and refresh.
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
        service = new DocsService(fx.roots(), new DocsCacheManager(), clock);
    }

    private DocsService service(String vault, List<String> open, List<String> adminOnly) {
        return new DocsService(new DocsRoots(vault, open, adminOnly), new DocsCacheManager(), clock);
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
        @DisplayName("admin sees all three sections as root folders, sorted case-insensitively")
        void adminTreeLayoutAndSorting() {
            DocsTreeNode tree = service.getFileTree(admin);

            assertThat(tree.name()).isEmpty();
            assertThat(tree.path()).isEmpty();
            assertThat(tree.type()).isEqualTo("folder");
            assertThat(tree.root()).isNull();
            assertThat(names(tree)).containsExactly(PERSONAL, HOME, LLCS);
            for (String label : List.of(PERSONAL, HOME, LLCS)) {
                DocsTreeNode section = child(tree, label);
                assertThat(section.root()).isTrue();
                assertThat(section.path()).isEqualTo(label);
                assertThat(section.type()).isEqualTo("folder");
            }
            assertThat(names(child(tree, HOME))).containsExactly("Home", "Insurance", "Sprinklers", "Alpha.md", "zeta.md");
            assertThat(child(child(tree, HOME), "Home").root()).isNull();
        }

        @Test
        @DisplayName("section children are prefixed with the section label, so tree paths are vault paths")
        void sectionPathsArePrefixed() {
            DocsTreeNode llcs = child(service.getFileTree(admin), LLCS);

            assertThat(names(llcs)).containsExactly("LegendaryEpics");
            assertThat(names(child(llcs, "LegendaryEpics"))).containsExactly("IP Management", "Projects", "Amira Artwork.md");
            assertThat(find(llcs, DocsFixture.LE_AMIRA_NOTE)).isPresent();
            assertThat(find(llcs, DocsFixture.LE_AMIRA_BOARD)).get()
                    .satisfies(n -> {
                        assertThat(n.type()).isEqualTo("file");
                        assertThat(n.children()).isNull();
                        assertThat(n.root()).isNull();
                    });
            assertThat(find(llcs, LE + "/Projects/Development/Project SwordBreak/Attachments/Images/Concept/Characters/Amira/#ClairLineArt.jpg"))
                    .isPresent();
            assertThat(find(service.getFileTree(admin), PERSONAL + "/Accounts/Accounts.md")).isPresent();
        }

        @Test
        @DisplayName("empty and txt-only folders are pruned; only md, canvas, pdf and images are listed")
        void pruningAndExtensionFilter() {
            DocsTreeNode tree = service.getFileTree(admin);

            DocsTreeNode home = child(child(tree, HOME), "Home");
            assertThat(names(home)).containsExactly("Network", "Basement.md", "Screenshot 1.png", "shared.png");
            assertThat(names(child(home, "Network"))).containsExactly("Network Diagram.canvas");
            assertThat(find(tree, h("Home/Network/Network Diagram.canvas"))).isPresent();
            assertThat(names(child(child(tree, HOME), "Sprinklers"))).containsExactly("HowToGuide.pdf", "Rainbird.md");
            assertThat(find(tree, h("README.txt"))).isEmpty();
            assertThat(find(tree, h("Insurance/Car Insurance/ID Card.pdf"))).isPresent();
        }

        @Test
        @DisplayName("vault content outside the sections (Attachments, root notes) is never listed")
        void vaultRootNotListed() {
            DocsTreeNode tree = service.getFileTree(admin);

            assertThat(find(tree, "Attachments")).isEmpty();
            assertThat(find(tree, "Attachments/server.png")).isEmpty();
            assertThat(find(tree, "Welcome.md")).isEmpty();
        }

        @Test
        @DisplayName("dot-entries (.obsidian, .trash) are never listed")
        void dotEntriesHidden() {
            DocsTreeNode tree = service.getFileTree(admin);

            assertThat(find(tree, ".obsidian")).isEmpty();
            assertThat(find(tree, h(".trash"))).isEmpty();
            assertThat(find(tree, h(".trash/x.md"))).isEmpty();
            assertThat(find(tree, LE + "/.obsidian")).isEmpty();
            assertThat(find(tree, LE + "/IP Management/Lamaryah WorldBuilding/.obsidian")).isEmpty();
        }

        @Test
        @DisplayName("a non-admin sees only the open section")
        void nonAdminFiltered() {
            assertThat(names(service.getFileTree(regular))).containsExactly(HOME);
            assertThat(names(service.getFileTree(null))).containsExactly(HOME);
        }

        @Test
        @DisplayName("missing sections and a blank vault are skipped")
        void missingAndBlankSections() {
            DocsService missing = service(fx.vault.toString(), List.of(HOME, "Missing"), List.of());
            assertThat(names(missing.getFileTree(admin))).containsExactly(HOME);
            assertThatThrownBy(() -> missing.resolveTreePath("Missing/x.md", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);

            DocsService onlyAdmin = service(fx.vault.toString(), List.of(), List.of(LLCS));
            assertThat(names(onlyAdmin.getFileTree(admin))).containsExactly(LLCS);
            assertThat(onlyAdmin.getFileTree(regular).children()).isEmpty();
            assertThatThrownBy(() -> onlyAdmin.resolveTreePath(h("Alpha.md"), admin))
                    .isInstanceOf(DocsFileNotFoundException.class);

            assertThat(service("  ", List.of(HOME), List.of(LLCS)).getFileTree(admin).children()).isEmpty();
            assertThat(service(null, null, null).getFileTree(admin).children()).isEmpty();
        }

        @Test
        @DisplayName("an empty section is still listed")
        void emptySectionListed() throws IOException {
            Files.createDirectories(fx.vault.resolve("Empty"));
            DocsService s = service(fx.vault.toString(), List.of(HOME, "Empty"), List.of());

            assertThat(names(s.getFileTree(regular))).containsExactly("Empty", HOME);
            assertThat(child(s.getFileTree(regular), "Empty").children()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Section configuration
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Section configuration")
    class Sections {

        @Test
        @DisplayName("each folder becomes a section under the vault; admin folders are admin-only")
        void entriesFromFolders() {
            DocsRoots roots = fx.roots();

            assertThat(roots.vaultPath()).isEqualTo(fx.vault.toString());
            assertThat(roots.entries()).extracting(DocsRoots.Entry::label).containsExactly(PERSONAL, LLCS, HOME);
            assertThat(roots.entries()).allSatisfy(e -> {
                assertThat(Path.of(e.path())).isEqualTo(fx.vault.resolve(e.label()));
                assertThat(e.adminOnly()).isEqualTo(!e.label().equals(HOME));
            });
        }

        @Test
        @DisplayName("names that are not a single plain folder name are ignored")
        void invalidNamesIgnored() {
            DocsRoots roots = new DocsRoots(fx.vault.toString(),
                    List.of("", "  ", ".", "..", ".obsidian", "../outside", "Home and Family/Home",
                            "Home and Family\\Home", "C:x", "C:", "x\0y", " " + HOME + " "),
                    null);

            assertThat(roots.entries()).extracting(DocsRoots.Entry::label).containsExactly(HOME);
        }

        @Test
        @DisplayName("a folder in both lists is admin-only, and duplicates collapse")
        void bothListsAdminWins() {
            DocsRoots roots = new DocsRoots(fx.vault.toString(), List.of(LLCS, HOME, HOME), List.of(LLCS));

            assertThat(roots.entries()).extracting(DocsRoots.Entry::label).containsExactly(LLCS, HOME);
            assertThat(roots.entries().get(0).adminOnly()).isTrue();

            DocsService s = new DocsService(roots, new DocsCacheManager(), clock);
            assertThat(names(s.getFileTree(regular))).containsExactly(HOME);
        }

        @Test
        @DisplayName("every section shares the vault's scope")
        void sectionsShareTheVaultScope() throws IOException {
            List<DocsRoots.ActiveRoot> active = fx.roots().resolveActive();

            assertThat(active).hasSize(3);
            assertThat(active).allSatisfy(r -> assertThat(r.scope()).isEqualTo(real(fx.vault)));
        }
    }

    // -----------------------------------------------------------------------
    // Routing and traversal
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Routing and traversal")
    class Routing {

        @Test
        @DisplayName("tree paths resolve to real files in their section")
        void resolvesTreePaths() throws IOException {
            assertThat(service.resolveTreePath(h("Home/Basement.md"), regular).path())
                    .isEqualTo(real(fx.home.resolve("Home/Basement.md")));
            ResolvedFile le = service.resolveTreePath(DocsFixture.LE_AMIRA_BOARD, admin);
            assertThat(le.path()).isEqualTo(real(fx.lamaryah.resolve("Characters/Amira/Amira Board.canvas")));
            assertThat(le.root().label()).isEqualTo(LLCS);
            assertThat(le.scopeRelativePath()).isEqualTo(DocsFixture.LE_AMIRA_BOARD);
        }

        @Test
        @DisplayName("../ out of a section is 403, including into another section")
        void traversalIs403() {
            assertThatThrownBy(() -> service.resolveTreePath(h("../Welcome.md"), admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath(h("Home/../../Welcome.md"), admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath(LLCS + "/../../x", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            // An open section is never a way into an admin-only one, for anyone
            for (User u : new User[] {regular, admin}) {
                assertThatThrownBy(() -> service.resolveTreePath(h("../LLCs/LegendaryEpics/Amira Artwork.md"), u))
                        .isInstanceOf(DocsPathTraversalException.class);
                assertThatThrownBy(() -> service.resolveTreePath(h("../Dylan Personal/Accounts/Accounts.md"), u))
                        .isInstanceOf(DocsPathTraversalException.class);
                assertThatThrownBy(() -> service.resolveTreePath(h("Home/..\\..\\Dylan Personal\\Accounts\\Accounts.md"), u))
                        .isInstanceOfAny(DocsPathTraversalException.class, DocsFileNotFoundException.class);
            }
        }

        @Test
        @DisplayName("a path whose first segment is not a section label is 404")
        void unroutableIs404() {
            for (String path : List.of("../Welcome.md", "Welcome.md", "Attachments/server.png",
                    "home and family/Alpha.md", "Home/Basement.md", "Alpha.md")) {
                assertThatThrownBy(() -> service.resolveTreePath(path, admin))
                        .as(path)
                        .isInstanceOf(DocsFileNotFoundException.class);
            }
        }

        @Test
        @DisplayName("dot-entries are 404 even when reached through ..")
        void hiddenIs404() {
            assertThatThrownBy(() -> service.resolveTreePath(".obsidian/app.json", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath(LE + "/.obsidian/app.json", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath(h(".trash/x.md"), admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath(h("Insurance/../.trash/x.md"), admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath(h("Insurance\\..\\.trash\\x.md"), admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("absolute, rooted, drive-prefixed, NUL and blank inputs are rejected")
        void absoluteAndMalformedRejected() {
            String absolute = fx.home.resolve("Alpha.md").toAbsolutePath().toString();
            assertThatThrownBy(() -> service.resolveTreePath(absolute, admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath("/etc/passwd.md", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath("\\\\server\\share\\x.md", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath("C:x.md", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath(LLCS + "//etc/passwd.md", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath(LLCS + "/C:/x.md", admin))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveTreePath(h("Alpha\0.md"), admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath("", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath(LLCS, admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath(LLCS + "/", admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @EnabledOnOs(OS.WINDOWS)
        @DisplayName("Windows: alternate data streams and illegal names are 404, not a validation error")
        void windowsInvalidPathsAre404() {
            assertThatThrownBy(() -> service.resolveTreePath(h("Alpha.md:secret.md"), admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath(h("Home/a<b>.md"), admin))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("a non-admin gets 404 for anything in an admin-only section, existing or not")
        void nonAdminAdminSectionIs404() throws IOException {
            assertThatThrownBy(() -> service.resolveTreePath(LE + "/Amira Artwork.md", regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveTreePath(LLCS + "/nope.md", regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.getTextFile(PERSONAL + "/Accounts/Accounts.md", regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.getBinaryFile(PERSONAL + "/Accounts/private.png", regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.getTextFile(DocsFixture.LE_AMIRA_NOTE, null))
                    .isInstanceOf(DocsFileNotFoundException.class);

            assertThat(service.getTextFile(PERSONAL + "/Accounts/Accounts.md", admin).content()).startsWith("# Accounts");
            assertThat(service.getBinaryFile(PERSONAL + "/Accounts/private.png", admin).path())
                    .isEqualTo(real(fx.personal.resolve("Accounts/private.png")));
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
            TextFile md = service.getTextFile(h("Home/Basement.md"), regular);
            assertThat(md.content()).startsWith("# Basement");
            assertThat(md.mediaType().toString()).isEqualTo("text/markdown;charset=UTF-8");

            TextFile canvas = service.getTextFile(DocsFixture.LE_AMIRA_BOARD, admin);
            assertThat(canvas.content()).contains("Official_Amira_Artwork1.jpg");
            assertThat(canvas.mediaType().toString()).isEqualTo("application/json");
        }

        @Test
        @DisplayName("invalid UTF-8 is decoded leniently and a BOM is dropped")
        void lenientUtf8() throws IOException {
            DocsFixture.bytes(fx.home.resolve("bad.md"), new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i', (byte) 0xC3});
            assertThat(service.getTextFile(h("bad.md"), regular).content()).isEqualTo("hi\uFFFD");
        }

        @Test
        @DisplayName("wrong type for the endpoint is 400")
        void wrongTypeIs400() {
            assertThatThrownBy(() -> service.getTextFile(h("Insurance/Car Insurance/ID Card.pdf"), regular))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
            assertThatThrownBy(() -> service.getTextFile(h("README.txt"), regular))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
            assertThatThrownBy(() -> service.getBinaryFile(h("Home/Basement.md"), regular))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
            assertThatThrownBy(() -> service.getBinaryFile(h("Sprinklers/Pratt_Tessa_Rainbird.rbcf"), regular))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
            assertThatThrownBy(() -> service.getTextFile(h("Alpha.md."), regular))
                    .isInstanceOfAny(DocsInvalidFileTypeException.class, DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("binary files resolve for pdf and images, case-insensitively by extension")
        void binaryResolves() throws IOException {
            assertThat(service.getBinaryFile(h("Insurance/Car Insurance/ID Card.pdf"), regular).path())
                    .isEqualTo(real(fx.home.resolve("Insurance/Car Insurance/ID Card.pdf")));
            DocsFixture.bytes(fx.home.resolve("Home/UPPER.PNG"), DocsFixture.PNG_BYTES);
            assertThat(service.getBinaryFile(h("Home/UPPER.PNG"), regular).fileName()).isEqualTo("UPPER.PNG");
        }

        @Test
        @DisplayName("text files over 32 MB are 413")
        void tooLargeIs413() throws IOException {
            Path big = fx.home.resolve("Big/huge.md");
            Files.createDirectories(big.getParent());
            try (RandomAccessFile raf = new RandomAccessFile(big.toFile(), "rw")) {
                raf.setLength(DocsService.MAX_TEXT_BYTES + 1);
            }
            assertThatThrownBy(() -> service.getTextFile(h("Big/huge.md"), regular))
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
        @DisplayName("Amira Board: inner-folder-relative canvas file resolves in the former nested vault")
        void amiraBoardInnerVault() throws IOException {
            ResolvedFile f = service.resolveEmbed(DocsFixture.LE_AMIRA_BOARD,
                    "Attachments/Pictures/Amira/Official_Amira_Artwork1.jpg", true, admin);
            assertThat(f.path()).isEqualTo(real(fx.lamaryah.resolve("Attachments/Pictures/Amira/Official_Amira_Artwork1.jpg")));
            assertThat(f.scopeRelativePath())
                    .isEqualTo(LE + "/IP Management/Lamaryah WorldBuilding/Attachments/Pictures/Amira/Official_Amira_Artwork1.jpg");
        }

        @Test
        @DisplayName("Amira note: outer-folder-relative embed from inside a former nested vault")
        void amiraNoteOuterVault() throws IOException {
            ResolvedFile f = service.resolveEmbed(DocsFixture.LE_AMIRA_NOTE,
                    "IP Management/Lamaryah WorldBuilding/Attachments/Pictures/Amira/Official_Amira_Artwork2.jpg",
                    false, admin);
            assertThat(f.path()).isEqualTo(real(fx.lamaryah.resolve("Attachments/Pictures/Amira/Official_Amira_Artwork2.jpg")));
        }

        @Test
        @DisplayName("a section's canvas reaches the vault-level Attachments/")
        void vaultAttachmentsFromSection() throws IOException {
            ResolvedFile f = service.resolveEmbed(h("Home/Network/Network Diagram.canvas"),
                    "Attachments/server.png", true, regular);
            assertThat(f.path()).isEqualTo(real(fx.vault.resolve("Attachments/server.png")));
            assertThat(f.scopeRelativePath()).isEqualTo("Attachments/server.png");
            assertThat(f.root().label()).isEqualTo(HOME);
        }

        @Test
        @DisplayName("an open note never reaches an image stored in an admin-only section; an admin's does")
        void adminSectionImagesHiddenFromOpenNotes() throws IOException {
            String note = h("Home/Basement.md");
            assertThatThrownBy(() -> service.resolveEmbed(note, "private.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveEmbed(note, "../../Dylan Personal/Accounts/private.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);

            Path expected = real(fx.personal.resolve("Accounts/private.png"));
            assertThat(service.resolveEmbed(note, "private.png", false, admin).path()).isEqualTo(expected);
            assertThat(service.resolveEmbed(PERSONAL + "/Accounts/Accounts.md", "private.png", false, admin).path())
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("duplicate names resolve to the closest file")
        void duplicatesByProximity() throws IOException {
            // Ancestor walk: Home/shared.png beats Vault/Attachments/shared.png
            assertThat(service.resolveEmbed(h("Home/Basement.md"), "shared.png", false, regular).path())
                    .isEqualTo(real(fx.home.resolve("Home/shared.png")));
            assertThat(service.resolveEmbed(h("Home/Network/Network Diagram.canvas"), "shared.png", false, regular).path())
                    .isEqualTo(real(fx.home.resolve("Home/shared.png")));
            // Name index: each note gets the icon.png of its own former inner vault
            assertThat(service.resolveEmbed(DocsFixture.LE_AMIRA_NOTE, "icon.png", false, admin).path())
                    .isEqualTo(real(fx.swordBreak.resolve("Attachments/icon.png")));
            assertThat(service.resolveEmbed(LE + "/IP Management/Lamaryah WorldBuilding/Characters/Amira/Amira.md",
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
            Path expected = real(fx.vault.resolve("Attachments/" + DocsFixture.CAFE_NFC));
            assertThat(service.resolveEmbed(h("Home/Basement.md"), DocsFixture.CAFE_NFD, false, regular).path())
                    .isEqualTo(expected);
            assertThat(service.resolveEmbed(h("Home/Basement.md"), "CAF\u00c9.PNG", false, regular).path())
                    .isEqualTo(expected);
            assertThat(service.resolveEmbed(h("Home/Basement.md"), "screenshot 1.PNG", false, regular).path())
                    .isEqualTo(real(fx.home.resolve("Home/Screenshot 1.png")));
        }

        @Test
        @DisplayName("URL-decoded relative markdown image resolves; the raw form is not decoded again")
        void relativeMarkdownImage() throws IOException {
            assertThat(service.resolveEmbed(h("Home/Basement.md"), "../../Attachments/My Pic.png", false, regular).path())
                    .isEqualTo(real(fx.vault.resolve("Attachments/My Pic.png")));
            assertThatThrownBy(() -> service.resolveEmbed(h("Home/Basement.md"), "../../Attachments/My%20Pic.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("literal '#name.jpg' from a canvas resolves; non-literal strips it to nothing")
        void literalHashName() throws IOException {
            String target = "Attachments/Images/Concept/Characters/Amira/#ClairLineArt.jpg";
            String board = LE + "/Projects/Development/Project SwordBreak/Concept Board.canvas";
            assertThat(service.resolveEmbed(board, target, true, admin).path())
                    .isEqualTo(real(fx.swordBreak.resolve(target)));
            assertThatThrownBy(() -> service.resolveEmbed(board, target, false, admin))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
        }

        @Test
        @DisplayName("alias and size suffixes are stripped from Obsidian targets")
        void aliasStripped() throws IOException {
            assertThat(service.resolveEmbed(h("Home/Basement.md"), "logo.svg|200", false, regular).path())
                    .isEqualTo(real(fx.vault.resolve("Attachments/logo.svg")));
        }

        @Test
        @DisplayName("a target the note does not reference is 404 (reference gate)")
        void unreferencedIs404() {
            assertThatThrownBy(() -> service.resolveEmbed(h("Home/Basement.md"), "unreferenced.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveEmbed(h("Alpha.md"), "server.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("a note target (.md) is 400")
        void mdTargetIs400() {
            assertThatThrownBy(() -> service.resolveEmbed(h("Home/Basement.md"), "Alpha.md", false, regular))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
        }

        @Test
        @DisplayName("../../x.png leaving the vault scope is 404 even when referenced")
        void escapingScopeIs404() {
            assertThatThrownBy(() -> service.resolveEmbed(h("zeta.md"), "../../outside.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("files in dot-folders are never served, by path or by name")
        void hiddenNeverServed() {
            assertThatThrownBy(() -> service.resolveEmbed(h("Home/Basement.md"), "secret.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveEmbed(h("Home/Basement.md"), "trashed.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveEmbed(h("Home/Basement.md"), "../.trash/trashed.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("from must be a readable text doc; non-admins cannot embed from an admin-only section")
        void fromChecks() {
            assertThatThrownBy(() -> service.resolveEmbed(DocsFixture.LE_AMIRA_BOARD,
                    "Attachments/Pictures/Amira/Official_Amira_Artwork1.jpg", true, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveEmbed(PERSONAL + "/Accounts/Accounts.md", "private.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            assertThatThrownBy(() -> service.resolveEmbed(h("Insurance/Car Insurance/ID Card.pdf"), "x.png", false, regular))
                    .isInstanceOf(DocsInvalidFileTypeException.class);
            assertThatThrownBy(() -> service.resolveEmbed(h("../Welcome.md"), "server.png", false, regular))
                    .isInstanceOf(DocsPathTraversalException.class);
            assertThatThrownBy(() -> service.resolveEmbed("Welcome.md", "server.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
        }

        @Test
        @DisplayName("Obsidian's table-escaped pipe '![[pic.png\\|300]]' resolves pic.png (the frontend sends 'pic.png')")
        void escapedPipeTableEmbed() throws IOException {
            DocsFixture.text(fx.home.resolve("Gallery.md"), String.join("\n",
                    "| Picture | Width |",
                    "| --- | --- |",
                    "| ![[pic.png\\|300]] | 300 |",
                    ""));
            DocsFixture.bytes(fx.home.resolve("pic.png"), DocsFixture.PNG_BYTES);
            Path expected = real(fx.home.resolve("pic.png"));

            assertThat(service.resolveEmbed(h("Gallery.md"), "pic.png", false, regular).path()).isEqualTo(expected);
            assertThat(service.resolveEmbed(h("Gallery.md"), "pic.png", false, admin).path()).isEqualTo(expected);
            assertThat(service.resolveEmbed(h("Gallery.md"), "pic.png\\|300", false, regular).path()).isEqualTo(expected);
        }

        @Test
        @DisplayName("a reference scan that overflows the stack is a 404, cached so the note is not re-scanned")
        void referenceScanOverflowIsCached404() {
            AtomicInteger scans = new AtomicInteger();
            DocsService overflowing = new DocsService(fx.roots(), new DocsCacheManager(), clock, (content, canvas) -> {
                scans.incrementAndGet();
                throw new StackOverflowError("simulated pathological note");
            });

            for (int i = 0; i < 3; i++) {
                assertThatThrownBy(() -> overflowing.resolveEmbed(h("Home/Basement.md"), "shared.png", false, regular))
                        .isInstanceOf(DocsFileNotFoundException.class);
            }
            assertThat(scans).as("scanned once per file version").hasValue(1);
        }

        @Test
        @DisplayName("an edited note updates its reference set")
        void referenceCacheInvalidatedOnChange() throws IOException {
            assertThatThrownBy(() -> service.resolveEmbed(h("Alpha.md"), "server.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);
            DocsFixture.text(fx.home.resolve("Alpha.md"), "# Alpha\n![[server.png]]\n");
            Files.setLastModifiedTime(fx.home.resolve("Alpha.md"),
                    java.nio.file.attribute.FileTime.from(Instant.parse("2030-01-01T00:00:00Z")));
            assertThat(service.resolveEmbed(h("Alpha.md"), "server.png", false, regular).path())
                    .isEqualTo(real(fx.vault.resolve("Attachments/server.png")));
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
            assertThat(find(service.getFileTree(admin), h("New.md"))).isEmpty();
            DocsFixture.text(fx.home.resolve("New.md"), "# new\n");

            clock.advance(Duration.ofSeconds(5));
            service.refresh();
            assertThat(find(service.getFileTree(admin), h("New.md"))).as("throttled").isEmpty();

            clock.advance(Duration.ofSeconds(6));
            service.refresh();
            assertThat(find(service.getFileTree(admin), h("New.md"))).isPresent();
        }

        @Test
        @DisplayName("a wall clock stepped backwards does not suppress refreshes")
        void clockSteppedBackwardsStillRefreshes() throws IOException {
            assertThat(find(service.getFileTree(admin), h("New.md"))).isEmpty();
            DocsFixture.text(fx.home.resolve("New.md"), "# new\n");

            clock.advance(Duration.ofHours(-2));
            service.refresh();
            assertThat(find(service.getFileTree(admin), h("New.md"))).as("refreshed after the step back").isPresent();

            // The throttle restarts from the rebuilt snapshot's time.
            DocsFixture.text(fx.home.resolve("Newer.md"), "# newer\n");
            clock.advance(Duration.ofSeconds(5));
            service.refresh();
            assertThat(find(service.getFileTree(admin), h("Newer.md"))).as("throttled").isEmpty();
        }

        @Test
        @DisplayName("refresh re-checks sections: a folder that appears later becomes active")
        void sectionAppearsLater() throws IOException {
            DocsService s = service(fx.vault.toString(), List.of(HOME), List.of("Later"));
            assertThat(names(s.getFileTree(admin))).doesNotContain("Later");

            DocsFixture.text(fx.vault.resolve("Later/Capitol.md"), "# capitol\n");
            clock.advance(Duration.ofSeconds(11));
            s.refresh();
            DocsTreeNode tree = s.getFileTree(admin);
            assertThat(child(tree, "Later").root()).isTrue();
            assertThat(find(tree, "Later/Capitol.md")).isPresent();
            assertThat(names(s.getFileTree(regular))).doesNotContain("Later");
        }

        @Test
        @DisplayName("new embed targets are found by name after a refresh")
        void embedIndexRefreshed() throws IOException {
            DocsFixture.text(fx.home.resolve("Alpha.md"), "![[late.png]]\n");
            Files.setLastModifiedTime(fx.home.resolve("Alpha.md"),
                    java.nio.file.attribute.FileTime.from(Instant.parse("2030-01-01T00:00:00Z")));
            service.getFileTree(admin);
            DocsFixture.bytes(fx.vault.resolve("Attachments/Deep/late.png"), DocsFixture.PNG_BYTES);
            assertThatThrownBy(() -> service.resolveEmbed(h("Alpha.md"), "late.png", false, regular))
                    .isInstanceOf(DocsFileNotFoundException.class);

            clock.advance(Duration.ofSeconds(11));
            service.refresh();
            assertThat(service.resolveEmbed(h("Alpha.md"), "late.png", false, regular).path())
                    .isEqualTo(real(fx.vault.resolve("Attachments/Deep/late.png")));
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
        @DisplayName("Windows junctions: loops terminate, targets outside the section are not listed or served")
        void junctions() throws Exception {
            Path loop = fx.home.resolve("Home/loop");
            Path out = fx.home.resolve("Home/out");
            Path intoAdmin = fx.home.resolve("Home/personal");
            assumeTrue(mklinkJunction(loop, fx.home), "could not create a junction");
            try {
                assumeTrue(mklinkJunction(out, fx.vault.resolve("Attachments")), "could not create a junction");
                assumeTrue(mklinkJunction(intoAdmin, fx.personal), "could not create a junction");
                clock.advance(Duration.ofSeconds(11));
                DocsTreeNode tree = service.getFileTree(admin);

                assertThat(find(tree, h("Home/out/server.png"))).isEmpty();
                assertThat(find(tree, h("Home/loop/Home/loop"))).isEmpty();
                assertThat(find(tree, h("Home/personal/Accounts/Accounts.md"))).isEmpty();
                assertThatThrownBy(() -> service.getBinaryFile(h("Home/out/server.png"), admin))
                        .isInstanceOf(DocsFileNotFoundException.class);
                assertThatThrownBy(() -> service.getTextFile(h("Home/personal/Accounts/Accounts.md"), regular))
                        .isInstanceOf(DocsFileNotFoundException.class);
                // The embed index walked the vault without looping and still resolves by name.
                assertThat(service.resolveEmbed(h("Home/Network/Network Diagram.canvas"), "Attachments/server.png", true, admin).path())
                        .isEqualTo(real(fx.vault.resolve("Attachments/server.png")));
            } finally {
                Files.deleteIfExists(intoAdmin);
                Files.deleteIfExists(out);
                Files.deleteIfExists(loop);
            }
        }

        @Test
        @DisplayName("symlinks leaving the section are not listed or served")
        void symlinks() throws IOException {
            Path link = fx.home.resolve("Home/link.png");
            Path dirLink = fx.home.resolve("Home/dirlink");
            try {
                Files.createSymbolicLink(link, fx.base.resolve("outside.png"));
                Files.createSymbolicLink(dirLink, fx.vault.resolve("Attachments"));
            } catch (IOException | UnsupportedOperationException | SecurityException e) {
                assumeTrue(false, "symlinks not available: " + e.getMessage());
            }
            try {
                DocsTreeNode tree = service.getFileTree(admin);
                assertThat(find(tree, h("Home/link.png"))).isEmpty();
                assertThat(find(tree, h("Home/dirlink/server.png"))).isEmpty();
                assertThatThrownBy(() -> service.getBinaryFile(h("Home/link.png"), admin))
                        .isInstanceOf(DocsFileNotFoundException.class);
                assertThatThrownBy(() -> service.getBinaryFile(h("Home/dirlink/server.png"), admin))
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
