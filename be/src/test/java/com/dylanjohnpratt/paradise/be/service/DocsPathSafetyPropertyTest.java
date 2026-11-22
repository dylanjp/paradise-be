package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.exception.DocsFileNotFoundException;
import com.dylanjohnpratt.paradise.be.exception.DocsFileTooLargeException;
import com.dylanjohnpratt.paradise.be.exception.DocsInvalidFileTypeException;
import com.dylanjohnpratt.paradise.be.exception.DocsPathTraversalException;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.service.DocsService.ResolvedFile;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: docs-multi-root, Property: hostile paths never escape the allowed scope
class DocsPathSafetyPropertyTest {

    /** Separators and fragments that have broken path handling on Windows or Linux. */
    private static final String[] HOSTILE = {
            "..", ".", "...", "a.", ":", "C:", "c:", "//h/s", "\\\\?\\", "\\\\.\\", "\u0000", " ", "~",
            "%2e%2e", "%2f", "::$DATA", "|300", "#h", "^b", "?", "*", "<", ">", "\"", "\t", "\n", "․․"
    };

    /** Real names from the fixture, so a fair share of tries reach an existing file. */
    private static final String[] NAMES = {
            "Home and Family", "Home and Family", "LLCs", "Dylan Personal", "Home", "Basement.md", "Alpha.md",
            "zeta.md", "Insurance", "Car Insurance", "ID Card.pdf", "LegendaryEpics", "Amira Artwork.md",
            "IP Management", "Lamaryah WorldBuilding", "Attachments", "Pictures", "Amira",
            "Official_Amira_Artwork1.jpg", ".obsidian", "app.json", ".trash", "x.md", "trashed.png", "secret.png",
            "server.png", "shared.png", "icon.png", "outside.png", "Vault", "Accounts", "Accounts.md",
            "private.png", "Welcome.md", "Screenshot 1.png", "logo.svg", "My Pic.png", ".png", ".md",
            "Home and Family/Home/Basement.md", "Home and Family/Insurance/Car Insurance/ID Card.pdf",
            "LLCs/LegendaryEpics/Amira Artwork.md", "Home and Family/Home/../Alpha.md",
            "Home and Family/../LLCs/LegendaryEpics/Amira Artwork.md", "Dylan Personal/Accounts/private.png",
            "Home and Family/../Dylan Personal/Accounts/private.png"
    };

    private static final User ADMIN = new User("admin", "password", Set.of("ROLE_ADMIN", "ROLE_USER"));
    private static final User REGULAR = new User("tessa", "password", Set.of("ROLE_USER"));

    private Path tempDir;
    private DocsFixture fx;
    private DocsService service;
    private Path realBase;
    private Path realLlcs;
    private Path realPersonal;
    private final AtomicInteger counter = new AtomicInteger();

    @BeforeProperty
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("docs-safety-test-");
        fx = DocsFixture.create(tempDir);
        realBase = tempDir.toRealPath();
        realLlcs = fx.llcs.toRealPath();
        realPersonal = fx.personal.toRealPath();
        service = new DocsService(fx.roots(), new DocsCacheManager());
    }

    /**
     * For any hostile tree path, resolveTreePath either throws a Docs* exception or returns a listed
     * file type whose real path is inside the routed section, has no dot-element, and is never in an
     * admin-only section for a non-admin.
     */
    @Property(tries = 500)
    void treePathsStayInsideTheirRoot(@ForAll("hostilePaths") String path, @ForAll boolean asAdmin) {
        User user = asAdmin ? ADMIN : REGULAR;
        ResolvedFile file;
        try {
            file = service.resolveTreePath(path, user);
        } catch (DocsFileNotFoundException | DocsPathTraversalException | DocsInvalidFileTypeException
                 | DocsFileTooLargeException expected) {
            return;
        }
        Path real = file.path();
        assertThat(real.startsWith(file.root().dir())).isTrue();
        assertThat(hasHiddenElement(file.root().dir().relativize(real))).isFalse();
        assertThat(DocsFileTypes.isAllowed(real.getFileName().toString())).isTrue();
        assertThat(Files.isRegularFile(real)).isTrue();
        if (!asAdmin) {
            assertThat(file.root().adminOnly()).isFalse();
            assertThat(real.startsWith(realLlcs)).isFalse();
            assertThat(real.startsWith(realPersonal)).isFalse();
        }
    }

    /**
     * For any hostile embed target referenced by a note, resolveEmbed either throws a Docs* exception or
     * returns a pdf/image inside the vault scope, with no dot-element, never outside the fixture,
     * and never in an admin-only section for a non-admin.
     */
    @Property(tries = 400)
    void embedsStayInsideTheVaultScope(@ForAll("hostileTargets") String target,
                                       @ForAll boolean literal,
                                       @ForAll boolean fromLe,
                                       @ForAll boolean asAdmin) throws IOException {
        // A fresh note that references the target both ways, so most tries pass the reference gate.
        String name = "gen-" + counter.incrementAndGet() + ".md";
        Path dir = fromLe ? fx.lamaryah.resolve("Characters/Gen") : fx.home.resolve("Home/Gen");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), "![[" + target + "]]\n![](" + target + ")\n", StandardCharsets.UTF_8);
        String from = (fromLe ? DocsFixture.LE + "/IP Management/Lamaryah WorldBuilding/Characters/Gen/"
                : DocsFixture.h("Home/Gen/")) + name;
        User user = asAdmin ? ADMIN : REGULAR;

        ResolvedFile file;
        try {
            file = service.resolveEmbed(from, target, literal, user);
        } catch (DocsFileNotFoundException | DocsPathTraversalException | DocsInvalidFileTypeException
                 | DocsFileTooLargeException expected) {
            return;
        }
        Path real = file.path();
        Path scope = file.root().scope();
        assertThat(real.startsWith(scope)).isTrue();
        assertThat(real.startsWith(realBase)).isTrue();
        assertThat(hasHiddenElement(scope.relativize(real))).isFalse();
        assertThat(DocsFileTypes.isBinary(real.getFileName().toString())).isTrue();
        assertThat(Files.isRegularFile(real)).isTrue();
        if (!asAdmin) {
            assertThat(real.startsWith(realLlcs)).isFalse();
            assertThat(real.startsWith(realPersonal)).isFalse();
        }
    }

    /**
     * For any string, the cleaner either throws a Docs* exception or returns a relative, root-less,
     * NUL-free pdf/image path.
     */
    @Property(tries = 1000)
    void cleanerOutputIsAlwaysRelative(@ForAll("anyTargets") String target, @ForAll boolean literal) {
        String cleaned;
        try {
            cleaned = DocsEmbedTargetCleaner.clean(target, literal);
        } catch (DocsFileNotFoundException | DocsInvalidFileTypeException expected) {
            return;
        }
        assertThat(cleaned).doesNotStartWith("/").doesNotContain("\u0000").doesNotContain("\\");
        assertThat(cleaned).doesNotMatch("(?s)^[A-Za-z]:.*");
        Path p = Path.of(cleaned);
        assertThat(p.getRoot()).isNull();
        assertThat(p.isAbsolute()).isFalse();
        assertThat(DocsFileTypes.isBinary(cleaned)).isTrue();
    }

    @Provide
    Arbitrary<String> hostilePaths() {
        return tokens().list().ofMinSize(1).ofMaxSize(10).map(parts -> String.join("", parts));
    }

    @Provide
    Arbitrary<String> hostileTargets() {
        return hostilePaths().filter(s -> s.indexOf('\n') < 0);
    }

    @Provide
    Arbitrary<String> anyTargets() {
        return Arbitraries.oneOf(hostilePaths(), Arbitraries.strings().all().ofMaxLength(40),
                Arbitraries.strings().all().ofMaxLength(20).map(s -> s + ".png"));
    }

    private Arbitrary<String> tokens() {
        return Arbitraries.frequencyOf(
                Tuple.of(4, Arbitraries.of(NAMES)),
                Tuple.of(3, Arbitraries.of("/", "/", "\\")),
                Tuple.of(3, Arbitraries.of(HOSTILE)));
    }

    private static boolean hasHiddenElement(Path relative) {
        for (Path element : relative) {
            if (element.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    @AfterProperty
    void cleanup() throws IOException {
        DocsFixture.deleteTree(tempDir);
    }
}
