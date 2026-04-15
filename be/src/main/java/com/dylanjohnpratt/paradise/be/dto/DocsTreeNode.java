package com.dylanjohnpratt.paradise.be.dto;

import java.util.List;

/**
 * Represents a node in the documentation file tree.
 * For folders, children is a non-null list; for files, children is null.
 */
public record DocsTreeNode(
    String name,
    String type,
    String path,
    List<DocsTreeNode> children
) {}
