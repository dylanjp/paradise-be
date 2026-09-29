package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.dto.DocsTreeNode;
import com.dylanjohnpratt.paradise.be.service.DocsRoots.ActiveRoot;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable result of one docs refresh.
 *
 * @param fullTree   the unfiltered tree (vault roots included); filtered per user by {@link DocsService}
 * @param roots      the roots that existed at refresh time
 * @param embedIndex per root, a map from {@link DocsFileTypes#key(String)} of a binary file name to every
 *                   matching file inside that root's vault scope (dot-folders excluded)
 */
public record DocsSnapshot(
    DocsTreeNode fullTree,
    List<ActiveRoot> roots,
    Map<ActiveRoot, Map<String, List<Path>>> embedIndex
) {

    /** Snapshot with an empty tree and no roots. */
    public static DocsSnapshot empty() {
        return new DocsSnapshot(new DocsTreeNode("", "folder", "", List.of(), null), List.of(), Map.of());
    }

    /** Finds the active root with the given label ("" for the main root). */
    public Optional<ActiveRoot> root(String label) {
        return roots.stream().filter(r -> r.label().equals(label)).findFirst();
    }
}
