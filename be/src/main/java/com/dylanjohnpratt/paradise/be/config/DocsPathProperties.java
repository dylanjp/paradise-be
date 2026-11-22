package com.dylanjohnpratt.paradise.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Filesystem source for the Documentation feature: one Obsidian vault, shown as one top-level
 * section per listed folder.
 *
 * @param vaultPath    the Obsidian vault folder (DOCS_VAULT_PATH)
 * @param folders      top-level vault folders shown to every signed-in user (DOCS_FOLDERS, comma-separated)
 * @param adminFolders top-level vault folders shown to ROLE_ADMIN only (DOCS_ADMIN_FOLDERS, comma-separated)
 */
@ConfigurationProperties(prefix = "docs")
public record DocsPathProperties(
    String vaultPath,
    List<String> folders,
    List<String> adminFolders
) {}
