package com.opslens.github;


/**
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
        String headBranch,
        String headSha,
        String baseBranch
){

}

