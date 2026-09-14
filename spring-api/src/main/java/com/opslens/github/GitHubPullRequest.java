package com.opslens.github;


/**
 *  Represents the GitHub pull request fields used by OpsLens
 *  A record is an immutable data carrier. Java automatically provides a constructor,
 *  accessors, equals(), hashCode(), and to String().
 * {
 *   "number": 42,
 *   "url": "https://api.github.com/repos/Jake6654/sketch-my-day/pulls/42",
 *   "html_url": "https://github.com/Jake6654/sketch-my-day/pull/42",
 *   "state": "open",
 *   "title": "[OpsLens] Fix incident #15",
 *   "draft": false,
 *   "head": {
 *     "ref": "ai-fix/inc-15-patch-20",
 *     "sha": "abc123"
 *   },
 *   "base": {
 *     "ref": "main"
 *   }
 * }
 */
public record GitHubPullRequest (
        Long number,
        String apiUrl,
        String htmlUrl,
        String state,
        String title,
        boolean draft,
        boolean merged,
        String headBranch,
        String headSha,
        String baseBranch
){

}

