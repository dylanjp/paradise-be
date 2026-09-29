package com.dylanjohnpratt.paradise.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Filesystem roots for the Documentation feature.
 *
 * @param path             main docs folder (DOCS_PATH), readable by every signed-in user
 * @param lePath           LegendaryEpics Obsidian vault (LE_DOCS_PATH), shown as "LE Docs", ROLE_ADMIN only
 * @param prattCapitolPath Pratt Capitol Obsidian vault (PRATT_CAPITOL_PATH), shown as "Pratt Capitol", ROLE_ADMIN only
 */
@ConfigurationProperties(prefix = "docs")
public record DocsPathProperties(
    String path,
    String lePath,
    String prattCapitolPath
) {}
