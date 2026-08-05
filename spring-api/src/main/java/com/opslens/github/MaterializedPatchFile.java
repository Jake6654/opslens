package com.opslens.github;

/**
 * Contains the complete file content produced after applying a unified diff
 */
public record MaterializedPatchFile(
        String path,
        String content
) {
}
