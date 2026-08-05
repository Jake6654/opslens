# Phase 5C: 검증된 패치를 GitHub 브랜치에 커밋하기

이 가이드는 다음 단계에 이어지는 내용입니다.

- Phase 5A: PR 사전 검사와 안전 계획 수립
- Phase 5B: 인증된 상태에서 안전하고 멱등성 있게 브랜치 생성

Phase 5C는 검증된 `suggestedDiff`를 AI가 만든 브랜치의 실제 Git commit으로
변환합니다. 아직 pull request를 열지는 않습니다. Pull request 생성은
Phase 5D에서 다룹니다.

## 1. 목표

목표로 하는 흐름은 다음과 같습니다.

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

Phase 5C 실행 전:

```text
main                         -> base SHA
ai-fix/inc-15-patch-20       -> base SHA
```

Phase 5C 실행 후:

```text
main                         -> base SHA
                                  \
ai-fix/inc-15-patch-20       -> new patch commit SHA
```

구현 과정에서는 다음 안전 규칙을 반드시 지켜야 합니다.

1. 유효하지 않은 패치를 절대 commit하지 않습니다.
2. 최신 테스트가 통과하지 않았다면 절대 commit하지 않습니다.
3. `main`, `master`, `production`, `release`에 직접 쓰지 않습니다.
4. 예상하지 못하게 이동한 브랜치를 절대 덮어쓰지 않습니다.
5. 브랜치를 업데이트할 때 절대 `force: true`를 사용하지 않습니다.
6. 같은 요청을 반복해도 중복 commit이 생성되면 안 됩니다.
7. Phase 5C MVP에서는 하나의 텍스트 파일 수정만 지원합니다.

## 2. 반드시 이해해야 하는 Git 객체

Git은 브랜치를 파일이 들어 있는 폴더 형태로 저장하지 않습니다. 관계는 다음과 같습니다.

```text
branch reference
  -> commit
    -> tree
      -> blob/file content
```

### Reference

브랜치는 다음과 같이 이름이 붙은 reference입니다.

```text
refs/heads/ai-fix/inc-15-patch-20
```

Reference는 하나의 commit SHA를 가리킵니다.

### Commit

Commit은 다음 정보를 저장합니다.

- commit message
- tree의 SHA
- 하나 이상의 부모 commit SHA

### Tree

Tree는 특정 시점의 repository 디렉터리와 파일 계층 구조를 나타냅니다.

### Blob

Blob은 파일 내용을 저장합니다. GitHub의 Create Tree endpoint는 `content`를
직접 받아 필요한 blob을 대신 생성할 수 있습니다.

따라서 Phase 5C에서는 다음 GitHub API 순서를 사용할 수 있습니다.

```text
GET   /git/ref/heads/{branch}
GET   /git/commits/{sha}
GET   /contents/{path}?ref={sha}
POST  /git/trees
POST  /git/commits
PATCH /git/refs/heads/{branch}
```

GitHub 문서에 따르면 `base_tree`를 사용해 tree를 생성하면 repository의 나머지
내용은 보존하면서 전달한 경로만 교체할 수 있습니다. Commit과 tree를 생성하려면
fine-grained token에 `Contents: read and write` 권한이 필요합니다.

## 3. Diff를 GitHub에 직접 보내지 않는 이유

GitHub Git Data API에는 unified diff를 받아 자동으로 적용하는 endpoint가
없습니다. 대신 변경 결과인 전체 파일 내용이나 Git 객체를 전달해야 합니다.

따라서 OpsLens는 다음 작업을 수행해야 합니다.

1. 정확한 branch SHA에서 원본 파일을 읽습니다.
2. 저장된 unified diff를 parsing합니다.
3. 원본 파일의 각 줄에 diff를 적용합니다.
4. 변경 결과를 새로운 tree entry로 GitHub에 전달합니다.

수동 `substring()` 연산으로 patch 적용을 구현하면 안 됩니다. Unified diff에는
hunk 위치, context line, 추가된 줄, 삭제된 줄이 들어 있습니다. 이 형식을 위해
만들어진 parser를 사용해야 합니다.

이 가이드에서는 `java-diff-utils` 버전 `4.17`을 사용합니다.

공식 참고 자료:

- https://central.sonatype.com/artifact/io.github.java-diff-utils/java-diff-utils
- https://github.com/java-diff-utils/java-diff-utils/wiki
- https://docs.github.com/en/rest/git/trees
- https://docs.github.com/en/rest/git/commits
- https://docs.github.com/en/rest/git/refs

## 4. Step 1: Unified Diff dependency 추가하기

다음 파일을 수정합니다.

```text
spring-api/build.gradle
```

`dependencies` 내부에 다음 내용을 추가합니다.

```gradle
implementation 'io.github.java-diff-utils:java-diff-utils:4.17'
```

이 dependency가 필요한 이유:

- `UnifiedDiffUtils.parseUnifiedDiff(...)`는 unified diff 텍스트를 구조화된
  `Patch<String>`으로 변환합니다.
- `Patch.applyTo(...)`는 patch를 원본 파일의 각 줄에 적용합니다.
- Diff context가 실제 파일과 일치하지 않으면 `PatchFailedException`을
  발생시킵니다.

마지막 동작은 특히 중요합니다. Context가 일치하지 않으면 파일 내용을 추측해서
만드는 대신 GitHub 변경 작업을 중단해야 합니다.

Gradle을 변경한 다음 IntelliJ에서 Gradle project를 다시 불러옵니다. Dependency가
backend image에도 포함되어야 하므로 Docker rebuild도 필요합니다.

## 5. Step 2: 작은 GitHub 데이터 record 추가하기

다음 파일을 생성합니다.

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

다음 파일을 생성합니다.

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

다음 파일을 생성합니다.

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

Record가 적합한 이유:

- 변경할 수 없는 데이터 운반 객체입니다.
- Java가 constructor와 accessor를 자동으로 생성합니다.
- 외부 API 결과는 parsing한 후 변경하지 않는 것이 안전합니다.

Record accessor는 `get`을 사용하지 않습니다.

```java
commit.sha();
commit.treeSha();
file.content();
```

## 6. Step 3: 실제 파일 내용으로 변환된 patch 표현하기

다음 파일을 생성합니다.

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

저장된 patch suggestion에는 diff가 들어 있지만 GitHub Tree API는 완성된 전체
파일 내용을 요구합니다. 이 record는 두 형식 사이의 변환 결과를 나타냅니다.

## 7. Step 4: Patch 변환 예외 정의하기

다음 파일을 생성합니다.

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

이 예외는 `GitHubApiException`과 역할이 다릅니다.

- `GitHubApiException`: GitHub와의 통신이 실패했습니다.
- `PatchMaterializationException`: diff를 parsing할 수 없거나 가져온 원본
  파일과 일치하지 않습니다.

## 8. Step 5: UnifiedDiffMaterializer 구현하기

다음 파일을 생성합니다.

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

### 주요 로직

`targetPath(...)`는 다음 입력을 거부합니다.

- 여러 파일을 수정하는 diff
- `/dev/null`을 통한 파일 생성 또는 삭제
- 파일 이름 변경
- 절대 경로
- `..`를 이용한 path traversal
- Markdown code fence

`apply(...)`는 patch를 비슷한 위치에 추측해서 적용하지 않습니다. Context line이
일치하지 않으면 예외를 발생시키고 commit workflow를 중단합니다.

하나의 파일만 허용하는 것은 의도적인 제한입니다. 최종 안전 규칙은 최대 세 개의
파일을 허용하지만, 최초의 실제 GitHub 변경에서는 한 파일 commit이 더 쉽게
검증할 수 있습니다.

## 9. Step 6: GitHubClient 확장하기

다음 파일을 수정합니다.

```text
spring-api/src/main/java/com/opslens/github/GitHubClient.java
```

기존 Phase 5B 메서드는 유지하고 아래 메서드를 추가합니다. 다음 import도
추가합니다.

```java
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
```

### Git commit 읽기

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

### 정확한 commit SHA에서 파일 읽기

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

### 수정된 파일이 포함된 tree 생성하기

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

`base_tree`를 사용하는 것은 매우 중요합니다. 이를 생략하면 새로운 tree에는
요청으로 전달한 파일만 포함되고, repository의 나머지 파일이 모두 삭제된 것처럼
보일 수 있습니다.

### Commit 객체 생성하기

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

### Force 없이 AI 브랜치 이동하기

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

절대로 다음과 같이 변경하면 안 됩니다.

```java
payload.put("force", true);
```

`force: false`는 fast-forward update를 요구합니다. Workflow 도중 누군가가 다른
commit을 브랜치에 push하면 GitHub는 그 작업을 잃게 만드는 대신 OpsLens의
업데이트를 거절합니다.

### JSON parsing helper 추가하기

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

마지막으로 기존 serializer의 parameter type을 다음 코드에서:

```java
private String toJson(Map<String, String> payload)
```

다음 코드로 확장합니다.

```java
private String toJson(Object payload)
```

Tree와 commit payload에는 중첩된 list와 boolean이 들어가므로
`Map<String, String>`만으로는 충분하지 않습니다.

## 10. Step 7: 공개 Commit 응답 정의하기

다음 파일을 생성합니다.

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

가능한 status 값:

```text
CREATED
ALREADY_COMMITTED
```

충돌과 validation 실패는 2xx가 아닌 HTTP response로 반환합니다.

## 11. Step 8: PullRequestCommitService 구현하기

다음 파일을 생성합니다.

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

### Commit marker가 필요한 이유

Commit message에는 다음 marker가 들어갑니다.

```text
OpsLens-Patch-Suggestion: 20
```

요청을 다시 실행하면 service가 branch head를 읽습니다. 동일한 patch marker가
존재하면 새로운 commit을 생성하지 않고 `ALREADY_COMMITTED`를 반환합니다.

### 브랜치를 여러 번 확인하는 이유

브랜치는 다음 시점에 확인합니다.

1. 파일을 읽고 변경 결과를 만들기 전
2. Reference를 업데이트하기 직전
3. 마지막 `force: false` 업데이트를 GitHub가 처리할 때

이를 optimistic concurrency control이라고 합니다. OpsLens는 GitHub를 lock하지
않습니다. 대신 변경을 완료하기 전에 현재 상태가 예상했던 상태와 같은지
확인합니다.

## 12. Step 9: Controller endpoint 추가하기

다음 파일을 수정합니다.

```text
spring-api/src/main/java/com/opslens/controller/PullRequestController.java
```

다음 dependency field를 추가합니다.

```java
private final PullRequestCommitService commitService;
```

Constructor에도 추가합니다.

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

Endpoint를 추가합니다.

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

다음 import를 추가합니다.

```java
import com.opslens.dto.CreatePullRequestCommitResponse;
import com.opslens.service.PatchMaterializationException;
import com.opslens.service.PullRequestCommitService;
```

Diff를 실제 파일 내용으로 변환할 수 없을 때 사용할 handler를 추가합니다.

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

`422 Unprocessable Content`는 요청 자체는 이해했지만 patch를 사용해 안전한
repository 내용을 만들 수 없었다는 뜻입니다.

## 13. Step 10: UnifiedDiffMaterializer 테스트하기

다음 파일을 생성합니다.

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

첫 번째 테스트는 parser와 patch 적용을 검사합니다. 나머지 테스트는 안전하지
않은 patch 형태가 GitHub 변경 전에 거부된다는 것을 증명합니다.

## 14. Step 11: PullRequestCommitService 테스트하기

다음 파일을 생성합니다.

```text
spring-api/src/test/java/com/opslens/service/PullRequestCommitServiceTests.java
```

최소한 다음 경우를 테스트해야 합니다.

### 준비된 patch가 하나의 commit을 생성하는 경우

다음 순서를 mock으로 구성합니다.

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

그런 다음 다음 내용을 검증합니다.

```java
assertTrue(response.isCreated());
assertEquals("CREATED", response.getStatus());
assertEquals("new-commit-sha", response.getCommitSha());
```

### 차단된 preflight에서는 GitHub를 호출하지 않는 경우

```java
when(preflightService.buildPlan(20L))
        .thenReturn(blockedPlan());

assertThrows(
        PullRequestBlockedException.class,
        () -> commitService.createCommit(20L)
);

verifyNoInteractions(gitHubClient);
```

이것은 가장 중요한 negative test입니다. 실패한 테스트로는 GitHub 변경 작업을
실행할 수 없다는 것을 증명합니다.

### 기존 marker를 이용해 멱등성을 보장하는 경우

Message에 다음 내용이 있는 branch-head commit을 반환하도록 설정합니다.

```text
OpsLens-Patch-Suggestion: 20
```

그런 다음 다음 내용을 검증합니다.

```java
assertFalse(response.isCreated());
assertEquals("ALREADY_COMMITTED", response.getStatus());
verify(gitHubClient, never()).createTree(any(), any());
verify(gitHubClient, never()).createCommit(any(), any(), any());
verify(gitHubClient, never()).updateBranch(any(), any());
```

### 이동한 브랜치에서 conflict가 발생하는 경우

AI branch와 base branch에 서로 다른 SHA를 반환하고
`PullRequestBranchConflictException`이 발생하는지 검증합니다.

### Commit 생성 도중 브랜치가 이동하는 경우

첫 번째 AI branch 조회에서는 `base-sha`, 마지막 조회에서는 `other-sha`를
반환합니다. `updateBranch(...)`가 호출되지 않는지 검증합니다.

## 15. Step 12: 빌드하고 단위 테스트 실행하기

Spring backend 디렉터리에서 다음 명령을 실행합니다.

```bash
cd /Users/jake/Documents/my-project/opslens/spring-api
./gradlew test
```

IntelliJ가 `java-diff-utils`를 인식하지 못한다면 먼저 Gradle을 다시 불러옵니다.

Docker image에는 dependency layer가 포함되므로 단위 테스트가 통과한 후
backend를 다시 빌드합니다.

```bash
cd /Users/jake/Documents/my-project/opslens
docker compose up -d --build --force-recreate backend
```

정상적으로 시작되었는지 확인합니다.

```bash
docker compose ps
docker compose logs backend --tail 100
```

## 16. Step 13: Patch Suggestion 20으로 통합 테스트하기

### Preflight 다시 확인하기

```bash
curl \
  http://localhost:8081/patch-suggestions/20/pull-request/preflight \
  | jq
```

예상 결과:

```json
{
  "status": "READY_FOR_PR",
  "ready": true,
  "patchValid": true,
  "testsPassed": true
}
```

### Branch가 존재하는지 확인하기

```bash
curl -i -X POST \
  http://localhost:8081/patch-suggestions/20/pull-request/branch
```

다음 결과가 반환될 수 있습니다.

```text
CREATED
```

또는:

```text
ALREADY_EXISTS
```

Branch가 예상한 base SHA를 가리키고 있다면 두 결과 모두 정상입니다.

### Patch commit 생성하기

```bash
curl -i -X POST \
  http://localhost:8081/patch-suggestions/20/pull-request/commit
```

첫 번째 요청의 예상 응답:

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

### 같은 요청 다시 보내기

```bash
curl -i -X POST \
  http://localhost:8081/patch-suggestions/20/pull-request/commit
```

예상 결과:

```http
HTTP/1.1 200 OK
```

```json
{
  "status": "ALREADY_COMMITTED",
  "created": false
}
```

GitHub에 두 번째 commit이 생성되어서는 안 됩니다.

### GitHub에서 확인하기

다음 주소를 엽니다.

```text
https://github.com/Jake6654/sketch-my-day/tree/ai-fix/inc-15-patch-20
```

다음 내용을 확인합니다.

1. `main`이 변경되지 않았습니다.
2. AI branch에 정확히 하나의 새 commit만 있습니다.
3. Commit이 예상한 파일만 변경했습니다.
4. Commit message에 OpsLens patch marker가 포함되어 있습니다.

## 17. 예상되는 실패 사례

### 409 Conflict: branch가 존재하지 않음

원인:

```text
Phase 5B branch creation was not run.
```

해결 방법:

```bash
curl -X POST \
  http://localhost:8081/patch-suggestions/20/pull-request/branch
```

### 409 Conflict: branch가 이동함

원인:

```text
A developer or another process pushed a commit to the AI branch.
```

OpsLens는 의도적으로 작업을 중단합니다. Branch를 강제로 update해서는 안 됩니다.

### 422 Unprocessable Content: patch를 적용할 수 없음

원인:

```text
The GitHub file content does not match the context stored in suggestedDiff.
```

권장 해결 방법:

1. 최신 source를 대상으로 code search를 다시 실행합니다.
2. 새로운 patch suggestion을 생성합니다.
3. 새 patch를 검증하고 테스트합니다.
4. 새 patch ID를 사용하여 새로운 branch를 생성합니다.

### 502 Bad Gateway: GitHub가 API 요청을 거부함

다음 내용을 확인합니다.

- Backend container에 token이 존재하는지
- Token에 `Contents: read and write` 권한이 있는지
- Repository owner와 name이 올바른지
- API rate limit을 초과하지 않았는지
- Backend log에 기록된 GitHub response body

Debugging 중에도 token을 절대로 출력하지 마세요.

## 18. 이 설계를 여러 파일로 나눈 이유

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

이 구조는 Single Responsibility Principle을 따릅니다. Patch parsing, business
safety, HTTP communication, API presentation은 서로 다른 이유로 변경되므로
하나의 거대한 class 안에 함께 두지 않는 것이 좋습니다.

## 19. 중요한 MVP 한계

Phase 4의 테스트는 local `sketch-my-day` workspace를 대상으로 실행되었습니다.
Phase 5C는 정확한 GitHub branch SHA에서 source를 다시 읽고, 그 source에 diff가
문제없이 적용되는지 확인합니다. 이를 통해 source drift는 감지할 수 있지만 새로
생성한 GitHub tree를 대상으로 전체 test suite를 다시 실행하지는 않습니다.

현재의 single-project prototype에서는 다음 안전장치가 있으므로 이 방식도
허용 가능한 단계입니다.

- Patch는 이전 단계에서 `git apply --check`로 검증되었습니다.
- Test run이 통과했습니다.
- GitHub source가 diff context와 일치해야 합니다.
- 마지막 branch update는 강제 update가 아닙니다.
- 최종적으로 사람이 pull request를 검토합니다.

이후 hardening phase에서는 정확한 remote SHA로부터 격리된 checkout을 만들고,
그곳에 patch를 적용해 테스트를 실행한 후에만 commit을 생성하도록 개선할 수
있습니다.

## 20. Phase 완료 기준

다음 조건을 모두 만족하면 Phase 5C가 완료됩니다.

- 검증된 patch가 하나의 Git commit으로 만들어집니다.
- Commit은 AI branch에만 생성됩니다.
- Branch는 `force: false`로 update됩니다.
- 같은 요청을 반복하면 `ALREADY_COMMITTED`가 반환됩니다.
- 유효하지 않은 patch와 실패한 test는 GitHub mutation method를 호출하지 않습니다.
- Branch가 이동했다면 덮어쓰는 대신 conflict가 발생합니다.
- 새 commit SHA를 API response와 GitHub에서 모두 확인할 수 있습니다.

다음 phase는 다음과 같습니다.

```text
Phase 5D: Open and persist the GitHub pull request
```

Phase 5D에서는 Phase 5A에서 이미 준비한 branch, title, body를 사용하고,
중복 PR 방지, GitHub PR 생성, database 저장 기능을 추가합니다.
