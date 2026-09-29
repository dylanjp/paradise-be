package com.dylanjohnpratt.paradise.be.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Represents a node in the documentation file tree.
 * For folders, children is a non-null list; for files, children is null.
 * {@code root} is true only on the top-level folder of an extra vault ("LE Docs", "Pratt Capitol")
 * and is omitted from the JSON everywhere else.
 */
public record DocsTreeNode(
    String name,
    String type,
    String path,
    List<DocsTreeNode> children,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean root
) {}
