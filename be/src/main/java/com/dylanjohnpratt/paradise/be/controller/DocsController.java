package com.dylanjohnpratt.paradise.be.controller;

import com.dylanjohnpratt.paradise.be.dto.DocsTreeNode;
import com.dylanjohnpratt.paradise.be.service.DocsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/docs")
public class DocsController {

    private final DocsService docsService;

    public DocsController(DocsService docsService) {
        this.docsService = docsService;
    }

    @GetMapping("/tree")
    public ResponseEntity<DocsTreeNode> getTree() {
        DocsTreeNode tree = docsService.getFileTree();
        return ResponseEntity.ok(tree);
    }

    @GetMapping("/file")
    public ResponseEntity<String> getFile(@RequestParam String path) {
        String content = docsService.getFileContent(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown"))
                .body(content);
    }
}
