# Phase 5C: Commit a Validated Patch to the GitHub Branch

This guide continues from:

- Phase 5A: PR preflight and safety planning
- Phase 5B: authenticated, safe, idempotent branch creation

Phase 5C turns a validated `suggestedDiff` into a real Git commit on the
AI-created branch. It does not open a pull request yet. Pull-request creation
belongs to Phase 5D.

## 1. Goal

The target flow is:

```text
POST /patch-suggestions/{id}/pull-request/commit
  -> load the patch suggestion
  -> run preflight again
  -> verify the AI branch exists
  -> verify the branch has not moved unexpectedly
  -> read the original file from the exact GitHub commit
  -> parse and apply the unified diff
  -> create a new Git tree
  -> create a new Git commit
  -> move the AI branch to the new commit without force
  -> return the new commit SHA
```

Before Phase 5C:

```text
main                         -> base SHA
ai-fix/inc-15-patch-20       -> base SHA
```

After Phase 5C:

```text
main                         -> base SHA
                                  \
ai-fix/inc-15-patch-20       -> new patch commit SHA
```

The implementation must preserve these safety rules:

1. Never commit an invalid patch.
2. Never commit when the latest test did not pass.
3. Never write directly to `main`, `master`, `production`, or `release`.
4. Never overwrite a branch that moved unexpectedly.
5. Never use `force: true` when updating the branch.
6. Repeating the same request must not create duplicate commits.
7. Phase 5C supports one modified text file for the MVP.

## 2. Git Objects You Need to Understand

Git does not store a branch as a folder containing files. The relationship is:

```text
branch reference
  -> commit
    -> tree
      -> blob/file content
```

### Reference

A branch is a named reference such as:

```text
refs/heads/ai-fix/inc-15-patch-20
```

The reference points to a commit SHA.

### Commit

A commit stores:

- a commit message
- the SHA of a tree
- one or more parent commit SHAs

### Tree

A tree represents the repository directory and file hierarchy at one point in
time.

### Blob

A blob stores file content. GitHub's Create Tree endpoint can accept `content`
directly and create the blob for us.

This means Phase 5C can use the following GitHub API sequence:

```text
GET   /git/ref/heads/{branch}
GET   /git/commits/{sha}
GET   /contents/{path}?ref={sha}
POST  /git/trees
POST  /git/commits
PATCH /git/refs/heads/{branch}
```

GitHub documents that creating a tree with a `base_tree` preserves the rest of
the repository while replacing only the supplied paths. Creating commits and
trees requires `Contents: read and write` on a fine-grained token.

## 3. Why We Do Not Send the Diff Directly to GitHub

GitHub's Git Data API does not provide an endpoint that accepts a unified diff
and applies it automatically. It expects the resulting file content or Git
objects.

OpsLens therefore must:

1. Read the original file at the exact branch SHA.
2. Parse the stored unified diff.
3. Apply the diff to the original lines.
4. Send the resulting content to GitHub as a new tree entry.

Do not implement patch application with manual `substring()` operations. A
unified diff contains hunk positions, context lines, inserted lines, and removed
lines. Use a parser designed for that format.

For this guide, use `java-diff-utils` version `4.17`.

Official references:

- https://central.sonatype.com/artifact/io.github.java-diff-utils/java-diff-utils
- https://github.com/java-diff-utils/java-diff-utils/wiki
- https://docs.github.com/en/rest/git/trees
- https://docs.github.com/en/rest/git/commits
- https://docs.github.com/en/rest/git/refs

## 4. Step 1: Add the Unified Diff Dependency

Update:

```text
spring-api/build.gradle
```

Add this inside `dependencies`:

```gradle
implementation 'io.github.java-diff-utils:java-diff-utils:4.17'
```

Why this dependency exists:

- `UnifiedDiffUtils.parseUnifiedDiff(...)` converts unified diff text into a
  structured `Patch<String>`.
- `Patch.applyTo(...)` applies that patch to the original file lines.
- It throws `PatchFailedException` when the diff context does not match the
  actual file.

That last behavior is important. A context mismatch should stop GitHub mutation
instead of producing guessed file content.

After changing Gradle, reload the Gradle project in IntelliJ. A Docker rebuild
will also be required because the dependency must be added to the backend image.

## 5. Step 2: Add Small GitHub Data Records

Create:

```text
spring-api/src/main/java/com/opslens/github/GitHubCommitObject.java
```

```java
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
```

Create:

```text
spring-api/src/main/java/com/opslens/github/GitHubFileContent.java
```

```java
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
```

Create:

```text
spring-api/src/main/java/com/opslens/github/GitHubTreeReference.java
```

```java
package com.opslens.github;

/**
 * Represents the SHA returned after GitHub creates a tree.
 */
public record GitHubTreeReference(String sha) {
}
```

Why records are appropriate:

- They are immutable data carriers.
- Java generates the constructor and accessors automatically.
- External API results should not be modified after parsing.

Record accessors do not use `get`:

```java
commit.sha();
commit.treeSha();
file.content();
```

## 6. Step 3: Represent the Materialized Patch

Create:

```text
spring-api/src/main/java/com/opslens/github/MaterializedPatchFile.java
```

```java
package com.opslens.github;

/**
 * Contains the complete file content produced after applying a unified diff.
 */
public record MaterializedPatchFile(
        String path,
        String content
) {
}
```

The stored patch suggestion contains a diff. GitHub's tree API needs complete
file content. This record represents the conversion result between those two
formats.

## 7. Step 4: Define a Patch Materialization Exception

Create:

```text
spring-api/src/main/java/com/opslens/service/PatchMaterializationException.java
```

```java
package com.opslens.service;

/**
 * Indicates that a validated diff could not be applied to GitHub file content.
 */
public class PatchMaterializationException extends RuntimeException {

    public PatchMaterializationException(String message) {
        super(message);
    }

    public PatchMaterializationException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
```

This is different from `GitHubApiException`:

- `GitHubApiException`: communication with GitHub failed.
- `PatchMaterializationException`: the diff could not be parsed or did not
  match the fetched source file.

## 8. Step 5: Implement UnifiedDiffMaterializer

Create:

```text
spring-api/src/main/java/com/opslens/service/UnifiedDiffMaterializer.java
```

```java
package com.opslens.service;

import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.opslens.github.GitHubFileContent;
import com.opslens.github.MaterializedPatchFile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * Parses a one-file unified diff and applies it to exact GitHub file content.
 */
@Service
public class UnifiedDiffMaterializer {

    /**
     * Reads and validates the destination file path from a unified diff.
     */
    public String targetPath(String suggestedDiff) {
        List<String> lines = normalizedLines(suggestedDiff);

        List<String> oldHeaders = lines.stream()
                .filter(line -> line.startsWith("--- "))
                .toList();

        List<String> newHeaders = lines.stream()
                .filter(line -> line.startsWith("+++ "))
                .toList();

        if (oldHeaders.size() != 1 || newHeaders.size() != 1) {
            throw new PatchMaterializationException(
                    "Phase 5C requires a unified diff for exactly one file."
            );
        }

        String oldPath = parseHeaderPath(oldHeaders.getFirst(), "--- ");
        String newPath = parseHeaderPath(newHeaders.getFirst(), "+++ ");

        if ("/dev/null".equals(oldPath)
                || "/dev/null".equals(newPath)) {
            throw new PatchMaterializationException(
                    "New-file creation and deletion are not supported in "
                            + "the Phase 5C MVP."
            );
        }

        if (!oldPath.equals(newPath)) {
            throw new PatchMaterializationException(
                    "File renames are not supported in the Phase 5C MVP."
            );
        }

        validateRepositoryPath(newPath);
        return newPath;
    }

    /**
     * Applies the unified diff to the exact file content fetched from GitHub.
     */
    public MaterializedPatchFile apply(
            String suggestedDiff,
            GitHubFileContent originalFile
    ) {
        String targetPath = targetPath(suggestedDiff);

        if (!targetPath.equals(originalFile.path())) {
            throw new PatchMaterializationException(
                    "The diff target does not match the fetched GitHub file."
            );
        }

        List<String> diffLines = normalizedLines(suggestedDiff);
        List<String> originalLines = originalFile.content()
                .lines()
                .toList();

        try {
            Patch<String> patch =
                    UnifiedDiffUtils.parseUnifiedDiff(diffLines);

            List<String> patchedLines = patch.applyTo(originalLines);
            String patchedContent = String.join("\n", patchedLines);

            // Preserve the source file's final newline convention.
            if (originalFile.content().endsWith("\n")) {
                patchedContent += "\n";
            }

            return new MaterializedPatchFile(
                    targetPath,
                    patchedContent
            );
        } catch (PatchFailedException | RuntimeException error) {
            throw new PatchMaterializationException(
                    "The suggested diff does not apply to the GitHub file "
                            + "at the expected commit.",
                    error
            );
        }
    }

    private List<String> normalizedLines(String suggestedDiff) {
        if (suggestedDiff == null || suggestedDiff.isBlank()) {
            throw new PatchMaterializationException(
                    "A non-empty suggested diff is required."
            );
        }

        String normalized = suggestedDiff
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();

        if (normalized.startsWith("```")) {
            throw new PatchMaterializationException(
                    "Markdown code fences are not allowed in suggestedDiff."
            );
        }

        return normalized.lines().toList();
    }

    private String parseHeaderPath(
            String header,
            String prefix
    ) {
        String path = header.substring(prefix.length()).trim();

        // Unified diff timestamps, when present, follow a tab character.
        int tabIndex = path.indexOf('\t');
        if (tabIndex >= 0) {
            path = path.substring(0, tabIndex);
        }

        if (path.startsWith("a/") || path.startsWith("b/")) {
            path = path.substring(2);
        }

        return path;
    }

    private void validateRepositoryPath(String path) {
        if (path.isBlank()) {
            throw new PatchMaterializationException(
                    "The diff does not contain a target file path."
            );
        }

        Path normalized = Path.of(path).normalize();

        if (normalized.isAbsolute()
                || normalized.startsWith("..")
                || path.contains("\\")) {
            throw new PatchMaterializationException(
                    "The diff target path is unsafe: " + path
            );
        }

        String normalizedText = normalized.toString().replace('\\', '/');
        if (!normalizedText.equals(path)) {
            throw new PatchMaterializationException(
                    "The diff target path must already be normalized: "
                            + path
            );
        }
    }
}
```

### Important logic

`targetPath(...)` rejects:

- multiple files
- file creation or deletion through `/dev/null`
- renames
- absolute paths
- `..` path traversal
- markdown fences

`apply(...)` does not apply a patch approximately. If context lines do not
match, it throws an exception and stops the commit workflow.

The one-file limit is deliberate. The project safety rule allows up to three
files eventually, but one-file commit support is easier to validate for the
first real GitHub mutation.

## 9. Step 6: Extend GitHubClient

Update:

```text
spring-api/src/main/java/com/opslens/github/GitHubClient.java
```

Keep the existing Phase 5B methods and add the methods below. Also add these
imports:

```java
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
```

### Read a Git commit

```java
/**
 * Reads a Git commit object so the workflow can obtain its tree and parents.
 */
public GitHubCommitObject getCommit(String commitSha) {
    validateConfiguration();

    if (commitSha == null || commitSha.isBlank()) {
        throw new IllegalArgumentException("A commit SHA is required.");
    }

    URI uri = URI.create(
            repositoryApiUrl()
                    + "/git/commits/"
                    + encodePathValue(commitSha)
    );

    HttpResponse<String> response = send(
            requestBuilder(uri).GET().build()
    );

    requireStatus(response, 200, "read GitHub commit");
    return parseCommit(response.body());
}
```

### Read a file at an exact commit SHA

```java
/**
 * Reads one UTF-8 text file from an exact Git commit.
 */
public GitHubFileContent getFile(String path, String ref) {
    validateConfiguration();

    if (path == null || path.isBlank()) {
        throw new IllegalArgumentException("A repository file path is required.");
    }

    if (ref == null || ref.isBlank()) {
        throw new IllegalArgumentException("A Git reference is required.");
    }

    URI uri = URI.create(
            repositoryApiUrl()
                    + "/contents/"
                    + encodeRepositoryPath(path)
                    + "?ref="
                    + encodePathValue(ref)
    );

    HttpResponse<String> response = send(
            requestBuilder(uri).GET().build()
    );

    requireStatus(response, 200, "read GitHub file");
    return parseFile(response.body(), path);
}
```

### Create a tree containing the modified file

```java
/**
 * Creates a Git tree based on an existing tree with one replaced text file.
 */
public GitHubTreeReference createTree(
        String baseTreeSha,
        MaterializedPatchFile file
) {
    validateConfiguration();

    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", file.path());
    entry.put("mode", "100644");
    entry.put("type", "blob");
    entry.put("content", file.content());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("base_tree", baseTreeSha);
    payload.put("tree", List.of(entry));

    HttpRequest request = requestBuilder(
            URI.create(repositoryApiUrl() + "/git/trees")
    )
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
            .build();

    HttpResponse<String> response = send(request);
    requireStatus(response, 201, "create GitHub tree");

    return new GitHubTreeReference(
            requiredText(response.body(), "sha")
    );
}
```

Using `base_tree` is critical. Without it, the new tree would contain only the
file supplied in the request and could make every other repository file appear
deleted.

### Create a commit object

```java
/**
 * Creates a Git commit whose parent is the current AI branch head.
 */
public GitHubCommitObject createCommit(
        String message,
        String treeSha,
        String parentSha
) {
    validateConfiguration();

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("message", message);
    payload.put("tree", treeSha);
    payload.put("parents", List.of(parentSha));

    HttpRequest request = requestBuilder(
            URI.create(repositoryApiUrl() + "/git/commits")
    )
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
            .build();

    HttpResponse<String> response = send(request);
    requireStatus(response, 201, "create GitHub commit");
    return parseCommit(response.body());
}
```

### Move the AI branch without force

```java
/**
 * Moves a branch to a new commit using a non-force, fast-forward update.
 */
public GitHubBranchReference updateBranch(
        String branch,
        String commitSha
) {
    validateConfiguration();
    validateBranch(branch);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("sha", commitSha);
    payload.put("force", false);

    HttpRequest request = requestBuilder(
            URI.create(
                    repositoryApiUrl()
                            + "/git/refs/heads/"
                            + encodePathValue(branch)
            )
    )
            .header("Content-Type", "application/json")
            .method(
                    "PATCH",
                    HttpRequest.BodyPublishers.ofString(toJson(payload))
            )
            .build();

    HttpResponse<String> response = send(request);
    requireStatus(response, 200, "update GitHub branch");
    return parseReference(response.body());
}
```

Never change this to:

```java
payload.put("force", true);
```

`force: false` requires a fast-forward update. If someone pushes another commit
to the branch during the workflow, GitHub rejects the update instead of losing
their work.

### Add JSON parsing helpers

```java
private GitHubCommitObject parseCommit(String responseBody) {
    try {
        JsonNode root = objectMapper.readTree(responseBody);
        String sha = root.path("sha").asText();
        String treeSha = root.path("tree").path("sha").asText();
        String message = root.path("message").asText();

        List<String> parentShas = new ArrayList<>();
        root.path("parents").forEach(parent ->
                parentShas.add(parent.path("sha").asText())
        );

        if (sha.isBlank() || treeSha.isBlank()) {
            throw new GitHubApiException(
                    502,
                    "GitHub returned an invalid commit response."
            );
        }

        return new GitHubCommitObject(
                sha,
                treeSha,
                message,
                List.copyOf(parentShas)
        );
    } catch (JsonProcessingException error) {
        throw new GitHubApiException(
                "Could not parse the GitHub commit response.",
                error
        );
    }
}

private GitHubFileContent parseFile(
        String responseBody,
        String expectedPath
) {
    try {
        JsonNode root = objectMapper.readTree(responseBody);
        String path = root.path("path").asText();
        String blobSha = root.path("sha").asText();
        String encoding = root.path("encoding").asText();
        String encodedContent = root.path("content").asText();

        if (!"base64".equalsIgnoreCase(encoding)) {
            throw new GitHubApiException(
                    502,
                    "GitHub returned an unsupported file encoding."
            );
        }

        String content = new String(
                Base64.getMimeDecoder().decode(encodedContent),
                StandardCharsets.UTF_8
        );

        if (!expectedPath.equals(path) || blobSha.isBlank()) {
            throw new GitHubApiException(
                    502,
                    "GitHub returned an unexpected file response."
            );
        }

        return new GitHubFileContent(path, blobSha, content);
    } catch (JsonProcessingException | IllegalArgumentException error) {
        throw new GitHubApiException(
                "Could not parse the GitHub file response.",
                error
        );
    }
}

private String requiredText(String responseBody, String field) {
    try {
        String value = objectMapper.readTree(responseBody)
                .path(field)
                .asText();

        if (value.isBlank()) {
            throw new GitHubApiException(
                    502,
                    "GitHub response is missing field: " + field
            );
        }

        return value;
    } catch (JsonProcessingException error) {
        throw new GitHubApiException(
                "Could not parse the GitHub response.",
                error
        );
    }
}

private String encodeRepositoryPath(String path) {
    return List.of(path.split("/"))
            .stream()
            .map(this::encodePathValue)
            .collect(Collectors.joining("/"));
}
```

Finally, broaden the existing serializer signature from:

```java
private String toJson(Map<String, String> payload)
```

to:

```java
private String toJson(Object payload)
```

The tree and commit payloads contain nested lists and booleans, so
`Map<String, String>` is no longer sufficient.

## 10. Step 7: Define the Public Commit Response

Create:

```text
spring-api/src/main/java/com/opslens/dto/CreatePullRequestCommitResponse.java
```

```java
package com.opslens.dto;

import java.util.List;

public class CreatePullRequestCommitResponse {

    private final Long patchSuggestionId;
    private final Long incidentId;
    private final String repository;
    private final String branch;
    private final String previousCommitSha;
    private final String commitSha;
    private final String status;
    private final boolean created;
    private final List<String> changedFiles;

    public CreatePullRequestCommitResponse(
            Long patchSuggestionId,
            Long incidentId,
            String repository,
            String branch,
            String previousCommitSha,
            String commitSha,
            String status,
            boolean created,
            List<String> changedFiles
    ) {
        this.patchSuggestionId = patchSuggestionId;
        this.incidentId = incidentId;
        this.repository = repository;
        this.branch = branch;
        this.previousCommitSha = previousCommitSha;
        this.commitSha = commitSha;
        this.status = status;
        this.created = created;
        this.changedFiles = List.copyOf(changedFiles);
    }

    public Long getPatchSuggestionId() {
        return patchSuggestionId;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getRepository() {
        return repository;
    }

    public String getBranch() {
        return branch;
    }

    public String getPreviousCommitSha() {
        return previousCommitSha;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getStatus() {
        return status;
    }

    public boolean isCreated() {
        return created;
    }

    public List<String> getChangedFiles() {
        return changedFiles;
    }
}
```

Possible statuses:

```text
CREATED
ALREADY_COMMITTED
```

Conflict and validation failures are returned as non-2xx HTTP responses.

## 11. Step 8: Implement PullRequestCommitService

Create:

```text
spring-api/src/main/java/com/opslens/service/PullRequestCommitService.java
```

```java
package com.opslens.service;

import com.opslens.dto.CreatePullRequestCommitResponse;
import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.github.GitHubBranchReference;
import com.opslens.github.GitHubClient;
import com.opslens.github.GitHubCommitObject;
import com.opslens.github.GitHubFileContent;
import com.opslens.github.GitHubTreeReference;
import com.opslens.github.MaterializedPatchFile;
import com.opslens.model.PatchSuggestion;
import com.opslens.repository.PatchSuggestionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PullRequestCommitService {

    private static final String COMMIT_PREFIX =
            "[OpsLens] Apply patch suggestion #";

    private final PullRequestPreflightService preflightService;
    private final PatchSuggestionRepository patchSuggestionRepository;
    private final UnifiedDiffMaterializer diffMaterializer;
    private final GitHubClient gitHubClient;

    public PullRequestCommitService(
            PullRequestPreflightService preflightService,
            PatchSuggestionRepository patchSuggestionRepository,
            UnifiedDiffMaterializer diffMaterializer,
            GitHubClient gitHubClient
    ) {
        this.preflightService = preflightService;
        this.patchSuggestionRepository = patchSuggestionRepository;
        this.diffMaterializer = diffMaterializer;
        this.gitHubClient = gitHubClient;
    }

    /**
     * Materializes and commits a validated patch to its AI-created branch.
     */
    public CreatePullRequestCommitResponse createCommit(
            Long patchSuggestionId
    ) {
        // Safety state can change, so verify it immediately before mutation.
        PullRequestPreflightResponse plan =
                preflightService.buildPlan(patchSuggestionId);

        if (!plan.isReady()) {
            throw new PullRequestBlockedException(plan.getBlockers());
        }

        PatchSuggestion patch = patchSuggestionRepository
                .findById(patchSuggestionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Patch suggestion not found: " + patchSuggestionId
                ));

        GitHubBranchReference branchReference = gitHubClient
                .findBranch(plan.getProposedBranch())
                .orElseThrow(() -> new PullRequestBranchConflictException(
                        "The AI branch does not exist. Create it before "
                                + "creating a commit."
                ));

        GitHubCommitObject branchHead =
                gitHubClient.getCommit(branchReference.sha());

        String commitMarker = commitMarker(patchSuggestionId);

        // This makes retries idempotent after a successful commit.
        if (branchHead.message().contains(commitMarker)) {
            return alreadyCommitted(plan, branchHead, patch);
        }

        GitHubBranchReference baseReference = gitHubClient
                .findBranch(plan.getBaseBranch())
                .orElseThrow(() -> new PullRequestBranchConflictException(
                        "The configured base branch does not exist."
                ));

        // The branch must still be untouched since Phase 5B created it.
        if (!branchReference.sha().equals(baseReference.sha())) {
            throw new PullRequestBranchConflictException(
                    "The AI branch moved before OpsLens created its patch "
                            + "commit and will not be overwritten."
            );
        }

        String targetPath =
                diffMaterializer.targetPath(patch.getSuggestedDiff());

        // Read the exact file version represented by the branch SHA.
        GitHubFileContent originalFile = gitHubClient.getFile(
                targetPath,
                branchReference.sha()
        );

        MaterializedPatchFile patchedFile = diffMaterializer.apply(
                patch.getSuggestedDiff(),
                originalFile
        );

        GitHubTreeReference tree = gitHubClient.createTree(
                branchHead.treeSha(),
                patchedFile
        );

        GitHubCommitObject commit = gitHubClient.createCommit(
                buildCommitMessage(patchSuggestionId, patch),
                tree.sha(),
                branchReference.sha()
        );

        // Re-read the branch just before moving it to narrow the race window.
        GitHubBranchReference latestReference = gitHubClient
                .findBranch(plan.getProposedBranch())
                .orElseThrow(() -> new PullRequestBranchConflictException(
                        "The AI branch disappeared during commit creation."
                ));

        if (!latestReference.sha().equals(branchReference.sha())) {
            throw new PullRequestBranchConflictException(
                    "The AI branch changed during commit creation and will "
                            + "not be overwritten."
            );
        }

        // force=false in GitHubClient provides the final fast-forward guard.
        GitHubBranchReference updated = gitHubClient.updateBranch(
                plan.getProposedBranch(),
                commit.sha()
        );

        return new CreatePullRequestCommitResponse(
                patchSuggestionId,
                plan.getIncidentId(),
                plan.getRepository(),
                plan.getProposedBranch(),
                branchReference.sha(),
                updated.sha(),
                "CREATED",
                true,
                List.of(patchedFile.path())
        );
    }

    private CreatePullRequestCommitResponse alreadyCommitted(
            PullRequestPreflightResponse plan,
            GitHubCommitObject branchHead,
            PatchSuggestion patch
    ) {
        String previousSha = branchHead.parentShas().isEmpty()
                ? ""
                : branchHead.parentShas().getFirst();

        return new CreatePullRequestCommitResponse(
                plan.getPatchSuggestionId(),
                plan.getIncidentId(),
                plan.getRepository(),
                plan.getProposedBranch(),
                previousSha,
                branchHead.sha(),
                "ALREADY_COMMITTED",
                false,
                List.of(diffMaterializer.targetPath(
                        patch.getSuggestedDiff()
                ))
        );
    }

    private String buildCommitMessage(
            Long patchSuggestionId,
            PatchSuggestion patch
    ) {
        return COMMIT_PREFIX
                + patchSuggestionId
                + "\n\n"
                + safeSummary(patch.getPatchSummary())
                + "\n\n"
                + commitMarker(patchSuggestionId);
    }

    private String commitMarker(Long patchSuggestionId) {
        return "OpsLens-Patch-Suggestion: " + patchSuggestionId;
    }

    private String safeSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return "Apply the validated OpsLens patch suggestion.";
        }

        return summary.trim();
    }
}
```

### Why the commit marker exists

The commit message contains:

```text
OpsLens-Patch-Suggestion: 20
```

On a retry, the service reads the branch head. If the marker for the same patch
is present, it returns `ALREADY_COMMITTED` rather than creating another commit.

### Why the branch is checked more than once

The branch is checked:

1. Before reading and materializing the file.
2. Again immediately before updating the reference.
3. By GitHub during the final `force: false` update.

This is optimistic concurrency control. OpsLens does not lock GitHub. It checks
that the state still matches its expectation before completing the mutation.

## 12. Step 9: Add the Controller Endpoint

Update:

```text
spring-api/src/main/java/com/opslens/controller/PullRequestController.java
```

Add the dependency:

```java
private final PullRequestCommitService commitService;
```

Add it to the constructor:

```java
public PullRequestController(
        PullRequestPreflightService preflightService,
        PullRequestBranchService branchService,
        PullRequestCommitService commitService
) {
    this.preflightService = preflightService;
    this.branchService = branchService;
    this.commitService = commitService;
}
```

Add the endpoint:

```java
@PostMapping("/{id}/pull-request/commit")
public ResponseEntity<CreatePullRequestCommitResponse> createCommit(
        @PathVariable Long id
) {
    CreatePullRequestCommitResponse response =
            commitService.createCommit(id);

    if (response.isCreated()) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    return ResponseEntity.ok(response);
}
```

Add this import:

```java
import com.opslens.dto.CreatePullRequestCommitResponse;
import com.opslens.service.PatchMaterializationException;
import com.opslens.service.PullRequestCommitService;
```

Add a handler for a diff that cannot be materialized:

```java
@ExceptionHandler(PatchMaterializationException.class)
public ResponseEntity<Map<String, Object>> handlePatchMaterialization(
        PatchMaterializationException error
) {
    return errorResponse(
            HttpStatus.UNPROCESSABLE_CONTENT,
            error.getMessage(),
            null
    );
}
```

`422 Unprocessable Content` means the request was understood, but its patch
could not safely produce repository content.

## 13. Step 10: Test UnifiedDiffMaterializer

Create:

```text
spring-api/src/test/java/com/opslens/service/UnifiedDiffMaterializerTests.java
```

```java
package com.opslens.service;

import com.opslens.github.GitHubFileContent;
import com.opslens.github.MaterializedPatchFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnifiedDiffMaterializerTests {

    private final UnifiedDiffMaterializer materializer =
            new UnifiedDiffMaterializer();

    @Test
    void appliesOneFileUnifiedDiff() {
        String original = """
                public class Example {
                    String normalize(String input) {
                        return input.trim();
                    }
                }
                """;

        String diff = """
                --- a/src/Example.java
                +++ b/src/Example.java
                @@ -1,5 +1,8 @@
                 public class Example {
                     String normalize(String input) {
                +        if (input == null) {
                +            throw new IllegalArgumentException("input");
                +        }
                         return input.trim();
                     }
                 }
                """;

        MaterializedPatchFile result = materializer.apply(
                diff,
                new GitHubFileContent(
                        "src/Example.java",
                        "blob-sha",
                        original
                )
        );

        assertEquals("src/Example.java", result.path());
        assertEquals(true, result.content().contains("input == null"));
    }

    @Test
    void rejectsMultipleFiles() {
        String diff = """
                --- a/One.java
                +++ b/One.java
                @@ -1 +1 @@
                -old
                +new
                --- a/Two.java
                +++ b/Two.java
                @@ -1 +1 @@
                -old
                +new
                """;

        assertThrows(
                PatchMaterializationException.class,
                () -> materializer.targetPath(diff)
        );
    }

    @Test
    void rejectsPathTraversal() {
        String diff = """
                --- a/../secret.txt
                +++ b/../secret.txt
                @@ -1 +1 @@
                -old
                +new
                """;

        assertThrows(
                PatchMaterializationException.class,
                () -> materializer.targetPath(diff)
        );
    }
}
```

The first test checks the parser and patch application. The other tests prove
that unsafe patch shapes are rejected before GitHub mutation.

## 14. Step 11: Test PullRequestCommitService

Create:

```text
spring-api/src/test/java/com/opslens/service/PullRequestCommitServiceTests.java
```

At minimum, cover these cases:

### Ready patch creates one commit

Mock this sequence:

```text
preflight -> READY_FOR_PR
find AI branch -> base-sha
get branch commit -> tree-sha
find base branch -> base-sha
get file -> original content
materializer -> patched content
create tree -> new-tree-sha
create commit -> new-commit-sha
re-read AI branch -> base-sha
update branch -> new-commit-sha
```

Then assert:

```java
assertTrue(response.isCreated());
assertEquals("CREATED", response.getStatus());
assertEquals("new-commit-sha", response.getCommitSha());
```

### Blocked preflight never calls GitHub

```java
when(preflightService.buildPlan(20L))
        .thenReturn(blockedPlan());

assertThrows(
        PullRequestBlockedException.class,
        () -> commitService.createCommit(20L)
);

verifyNoInteractions(gitHubClient);
```

This is the most important negative test. It proves failed tests cannot trigger
a GitHub mutation.

### Existing marker is idempotent

Return a branch-head commit whose message contains:

```text
OpsLens-Patch-Suggestion: 20
```

Then assert:

```java
assertFalse(response.isCreated());
assertEquals("ALREADY_COMMITTED", response.getStatus());
verify(gitHubClient, never()).createTree(any(), any());
verify(gitHubClient, never()).createCommit(any(), any(), any());
verify(gitHubClient, never()).updateBranch(any(), any());
```

### Moved branch produces conflict

Return different SHAs for the AI branch and base branch, then assert
`PullRequestBranchConflictException`.

### Branch moves during commit creation

Return `base-sha` on the first AI branch lookup and `other-sha` on the final
lookup. Assert that `updateBranch(...)` is never called.

## 15. Step 12: Build and Run Unit Tests

From the Spring backend directory:

```bash
cd /Users/jake/Documents/my-project/opslens/spring-api
./gradlew test
```

If IntelliJ cannot resolve `java-diff-utils`, reload Gradle first.

Because Docker images contain dependency layers, rebuild the backend after the
unit tests pass:

```bash
cd /Users/jake/Documents/my-project/opslens
docker compose up -d --build --force-recreate backend
```

Check startup:

```bash
docker compose ps
docker compose logs backend --tail 100
```

## 16. Step 13: Integration Test with Patch Suggestion 20

### Check preflight again

```bash
curl \
  http://localhost:8081/patch-suggestions/20/pull-request/preflight \
  | jq
```

Expected:

```json
{
  "status": "READY_FOR_PR",
  "ready": true,
  "patchValid": true,
  "testsPassed": true
}
```

### Ensure the branch exists

```bash
curl -i -X POST \
  http://localhost:8081/patch-suggestions/20/pull-request/branch
```

It may return either:

```text
CREATED
```

or:

```text
ALREADY_EXISTS
```

Both are valid when the branch points to the expected base SHA.

### Create the patch commit

```bash
curl -i -X POST \
  http://localhost:8081/patch-suggestions/20/pull-request/commit
```

Expected first response:

```http
HTTP/1.1 201 Created
```

```json
{
  "patchSuggestionId": 20,
  "incidentId": 15,
  "repository": "Jake6654/sketch-my-day",
  "branch": "ai-fix/inc-15-patch-20",
  "previousCommitSha": "base-sha",
  "commitSha": "new-commit-sha",
  "status": "CREATED",
  "created": true,
  "changedFiles": [
    "backend/src/main/java/sketch_my_day/demo/debug/DebugFailureController.java"
  ]
}
```

### Repeat the same request

```bash
curl -i -X POST \
  http://localhost:8081/patch-suggestions/20/pull-request/commit
```

Expected:

```http
HTTP/1.1 200 OK
```

```json
{
  "status": "ALREADY_COMMITTED",
  "created": false
}
```

No second commit should appear on GitHub.

### Verify on GitHub

Open:

```text
https://github.com/Jake6654/sketch-my-day/tree/ai-fix/inc-15-patch-20
```

Confirm:

1. `main` was not modified.
2. The AI branch has exactly one new commit.
3. The commit changes only the expected file.
4. The commit message contains the OpsLens patch marker.

## 17. Expected Failure Cases

### 409 Conflict: branch missing

Cause:

```text
Phase 5B branch creation was not run.
```

Resolution:

```bash
curl -X POST \
  http://localhost:8081/patch-suggestions/20/pull-request/branch
```

### 409 Conflict: branch moved

Cause:

```text
A developer or another process pushed a commit to the AI branch.
```

OpsLens intentionally stops. It must not force-update the branch.

### 422 Unprocessable Content: patch does not apply

Cause:

```text
The GitHub file content does not match the context stored in suggestedDiff.
```

Likely resolution:

1. Run code search again against the latest source.
2. Generate a new patch suggestion.
3. Validate and test the new patch.
4. Create a new branch using the new patch ID.

### 502 Bad Gateway: GitHub rejected an API request

Check:

- token is present in the backend container
- token has `Contents: read and write`
- repository owner and name are correct
- API rate limit has not been exceeded
- GitHub response body in the backend logs

Never print the token while debugging.

## 18. Why This Design Is Split Across Files

```text
UnifiedDiffMaterializer
-> understands unified diff parsing and safe file materialization

GitHubClient
-> understands GitHub endpoints, authentication, JSON, and status codes

PullRequestPreflightService
-> decides whether any GitHub mutation is allowed

PullRequestCommitService
-> coordinates the safe commit transaction

PullRequestController
-> maps HTTP requests, success statuses, and domain errors

CreatePullRequestCommitResponse
-> defines the public API contract

Service tests
-> prove unsafe states do not mutate GitHub
```

This follows the Single Responsibility Principle. Patch parsing, business
safety, HTTP communication, and API presentation change for different reasons,
so they should not live in one large class.

## 19. Important MVP Limitation

Phase 4 tests ran against the local `sketch-my-day` workspace. Phase 5C reads
the source again from the exact GitHub branch SHA and requires the diff to apply
cleanly there. This catches source drift, but it does not rerun the entire test
suite against the newly created GitHub tree.

For the current single-project prototype, this is an acceptable guarded step
because:

- the patch was previously validated with `git apply --check`
- the test run passed
- the GitHub source must match the diff context
- the final branch update is non-force
- a human still reviews the pull request

A later hardening phase can create an isolated checkout from the exact remote
SHA, apply the patch there, run tests, and only then create the commit.

## 20. Phase Boundary

Phase 5C is complete when all of the following are true:

- a validated patch becomes one Git commit
- the commit is created only on the AI branch
- the branch is updated with `force: false`
- a repeated request returns `ALREADY_COMMITTED`
- invalid patches and failed tests never call GitHub mutation methods
- branch movement causes a conflict instead of an overwrite
- the new commit SHA is visible in the API response and on GitHub

The next phase is:

```text
Phase 5D: Open and persist the GitHub pull request
```

Phase 5D will use the branch, title, and body already prepared by Phase 5A and
will add duplicate-PR prevention, GitHub PR creation, and database persistence.
