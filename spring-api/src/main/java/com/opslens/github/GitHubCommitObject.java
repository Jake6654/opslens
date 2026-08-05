package com.opslens.github;

import java.util.List;

/**
 * Represents the Git commit fields required by the commit workflow.
 */
public record GitHubCommitObject(
        String sha,
        String treeSha,
        String message,
        List<String> parentShas
) {
}
