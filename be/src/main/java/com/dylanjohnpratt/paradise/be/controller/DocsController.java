package com.dylanjohnpratt.paradise.be.controller;

import com.dylanjohnpratt.paradise.be.dto.DocsTreeNode;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.service.DocsFileTypes;
import com.dylanjohnpratt.paradise.be.service.DocsService;
import com.dylanjohnpratt.paradise.be.service.DocsService.ResolvedFile;
import com.dylanjohnpratt.paradise.be.service.DocsService.TextFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Read-only API for the Documentation page.
 * Serves the per-user docs tree, text documents (md, canvas), binary previews (pdf, images) and
 * Obsidian embeds from DOCS_PATH and the admin-only "LE Docs" / "Pratt Capitol" vaults.
 * Binary responses are inline {@link FileSystemResource}s, so Spring adds Content-Length and
 * Range/206 support; Spring Security's default {@code Cache-Control: no-store} is kept on purpose so
 * vault files never land in disk caches.
 */
@RestController
@RequestMapping("/docs")
public class DocsController {

    private static final Logger log = LoggerFactory.getLogger(DocsController.class);

    /** Served with every SVG so a directly opened SVG cannot run script or load anything. */
    static final String SVG_CSP = "default-src 'none'; style-src 'unsafe-inline'; sandbox";

    private final DocsService docsService;

    public DocsController(DocsService docsService) {
        this.docsService = docsService;
    }

    /**
     * Returns the docs tree. Admin-only vault folders are omitted for users without ROLE_ADMIN.
     *
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the root {@link DocsTreeNode}
     */
    @GetMapping("/tree")
    public ResponseEntity<DocsTreeNode> getTree(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(docsService.getFileTree(currentUser));
    }

    /**
     * Rebuilds the tree and embed index (single-flight, throttled to one rebuild per 10 seconds)
     * and returns the refreshed tree.
     *
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the refreshed root {@link DocsTreeNode}
     */
    @PostMapping("/refresh")
    public ResponseEntity<DocsTreeNode> refresh(@AuthenticationPrincipal User currentUser) {
        docsService.refresh();
        log.info("AUDIT docs.refresh user={}", username(currentUser));
        return ResponseEntity.ok(docsService.getFileTree(currentUser));
    }

    /**
     * Returns a text document: Markdown as {@code text/markdown;charset=UTF-8}, Canvas as
     * {@code application/json}.
     *
     * @param path        the tree path, e.g. {@code Insurance/Insurance Overview.md}
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the document text
     */
    @GetMapping("/file")
    public ResponseEntity<String> getFile(@RequestParam String path,
                                          @AuthenticationPrincipal User currentUser) {
        TextFile file = docsService.getTextFile(path, currentUser);
        return ResponseEntity.ok()
                .contentType(file.mediaType())
                .body(file.content());
    }

    /**
     * Streams a pdf or image listed in the tree, inline.
     *
     * @param path        the tree path, e.g. {@code Insurance/Car Insurance/ID Card.pdf}
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the file as an inline resource
     */
    @GetMapping("/raw")
    public ResponseEntity<Resource> getRaw(@RequestParam String path,
                                           @AuthenticationPrincipal User currentUser) {
        ResolvedFile file = docsService.getBinaryFile(path, currentUser);
        log.info("AUDIT docs.raw user={} root={} path={}",
                username(currentUser), rootName(file), sanitizeForLog(file.scopeRelativePath()));
        return inline(file);
    }

    /**
     * Streams a pdf or image embedded by a note or canvas, resolved within the note's Obsidian vault.
     * Only targets the {@code from} document actually references are served.
     *
     * @param from        tree path of the md/canvas document making the embed
     * @param target      the embed target as written (e.g. {@code Attachments/pic.png} or {@code pic.png|300})
     * @param literal     true for canvas {@code file} values, which keep {@code | # ^} in the name
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the resolved file as an inline resource
     */
    @GetMapping("/embed")
    public ResponseEntity<Resource> getEmbed(@RequestParam String from,
                                             @RequestParam String target,
                                             @RequestParam(defaultValue = "false") boolean literal,
                                             @AuthenticationPrincipal User currentUser) {
        ResolvedFile file = docsService.resolveEmbed(from, target, literal, currentUser);
        log.info("AUDIT docs.embed user={} root={} path={}",
                username(currentUser), rootName(file), sanitizeForLog(file.scopeRelativePath()));
        return inline(file);
    }

    private static ResponseEntity<Resource> inline(ResolvedFile file) {
        String name = file.fileName();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(name, StandardCharsets.UTF_8)
                .build());
        if (DocsFileTypes.isSvg(name)) {
            headers.set("Content-Security-Policy", SVG_CSP);
        }
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(DocsFileTypes.mediaType(name))
                .body(new FileSystemResource(file.path()));
    }

    private static String username(User user) {
        return user == null ? "?" : user.getUsername();
    }

    private static String rootName(ResolvedFile file) {
        return file.root().isMain() ? "docs" : file.root().label();
    }

    private static String sanitizeForLog(String value) {
        return value.replace('\r', '_').replace('\n', '_');
    }
}
