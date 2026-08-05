package com.opslens.github;
/**
 * Represents one text file read from a specific GitHub commit.
 */
public record GitHubFileContent(
        String path,
        String blobSha,
        String content
) {
}
