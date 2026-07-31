# Phase 5B: GitHub Authentication and Safe Branch Creation

This guide explains how to implement Phase 5B after Phase 5A returns
`READY_FOR_PR`. It includes the complete recommended code, file responsibilities,
runtime flow, safety logic, important Java syntax, tests, and manual verification.

## 1. Goal

Phase 5A only calculated a pull request plan. Phase 5B performs the first real
GitHub mutation: creating a dedicated branch from the configured base branch.

```text
Phase 5A
Build a read-only PR plan
        |
        v
Phase 5B
Authenticate with GitHub
Recheck readiness
Read the base branch SHA
Create ai-fix/inc-{incidentId}-patch-{patchId}
```

Phase 5B does not apply the patch to GitHub, create a commit, or open a pull
request. Those operations belong to later Phase 5 steps.

## 2. Why GitHub Branches Are References

Git stores a branch as a named reference to a commit SHA.

```text
main -> abc123
```

Creating a new branch from `main` means creating another reference that points
to the same commit:

```text
main                         -> abc123
ai-fix/inc-15-patch-20       -> abc123
```

GitHub exposes this through its Git References REST API:

1. `GET /repos/{owner}/{repo}/git/ref/heads/{branch}` reads the base SHA.
2. `POST /repos/{owner}/{repo}/git/refs` creates the new reference.

Official reference:

https://docs.github.com/en/rest/git/refs

## 3. Security Model

For the current personal prototype, use a fine-grained personal access token
restricted to the `sketch-my-day` repository. Creating a Git reference requires
repository Contents write permission. A future SaaS version should replace the
personal token with short-lived GitHub App installation tokens.

The token must:

- live only in environment variables or a secret manager;
- never be committed to Git;
- never be stored in a database response;
- never be included in logs or exceptions; and
- never be sent to FastAPI or OpenAI.

GitHub authentication documentation:

https://docs.github.com/en/rest/authentication/authenticating-to-the-rest-api

## 4. Runtime Flow

```text
POST /patch-suggestions/20/pull-request/branch
        |
        v
PullRequestController
        |
        v
PullRequestBranchService.createBranch(20)
        |
        |-- PullRequestPreflightService.buildPlan(20)
        |-- Reject unless ready == true
        |-- GitHubClient.findBranch("main")
        |-- Read main commit SHA
        |-- GitHubClient.findBranch("ai-fix/inc-15-patch-20")
        |       |-- same SHA: return ALREADY_EXISTS
        |       `-- different SHA: stop with conflict
        |-- GitHubClient.createBranch(...)
        `-- return CREATED
```

The preflight is recalculated immediately before GitHub mutation. OpsLens does
not trust an old response because patch or test state may have changed.

## 5. Step 1: Add GitHub API Configuration

File:

`spring-api/src/main/resources/application.yml`

Complete recommended file:

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      data-source-properties:
        prepareThreshold: 0

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

server:
  port: ${PORT:8080}

ai:
  orchestrator:
    url: ${AI_ORCHESTRATOR_URL:http://localhost:8002}

github:
  api-base-url: ${GITHUB_API_BASE_URL:https://api.github.com}
  api-version: ${GITHUB_API_VERSION:2026-03-10}
  token: ${GITHUB_TOKEN:}
  owner: ${GITHUB_OWNER:Jake6654}
  repository: ${GITHUB_REPOSITORY:sketch-my-day}
  base-branch: ${GITHUB_BASE_BRANCH:main}
  branch-prefix: ${GITHUB_BRANCH_PREFIX:ai-fix}
```

`${GITHUB_TOKEN:}` defaults to an empty string, not a real secret. The service
will refuse to call GitHub when the value is blank.

Add the variables to the backend service in `docker-compose.yml`:

```yaml
backend:
  environment:
    DB_URL: jdbc:postgresql://postgres:5432/opslens
    DB_USERNAME: opslens
    DB_PASSWORD: opslens
    API_KEY: ${API_KEY}
    AI_ORCHESTRATOR_URL: http://ai-orchestrator:8000
    GITHUB_TOKEN: ${GITHUB_TOKEN}
    GITHUB_OWNER: ${GITHUB_OWNER:-Jake6654}
    GITHUB_REPOSITORY: ${GITHUB_REPOSITORY:-sketch-my-day}
    GITHUB_BASE_BRANCH: ${GITHUB_BASE_BRANCH:-main}
    GITHUB_BRANCH_PREFIX: ${GITHUB_BRANCH_PREFIX:-ai-fix}
```

Keep the real token only in the root `.env`, which must be ignored by Git:

```dotenv
GITHUB_TOKEN=your_fine_grained_token
GITHUB_OWNER=Jake6654
GITHUB_REPOSITORY=sketch-my-day
GITHUB_BASE_BRANCH=main
GITHUB_BRANCH_PREFIX=ai-fix
```

## 6. Step 2: Extend GitHubProperties

File:

`spring-api/src/main/java/com/opslens/config/GitHubProperties.java`

Complete code:

```java
package com.opslens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    private String apiBaseUrl;
    private String apiVersion;
    private String token;
    private String owner;
    private String repository;
    private String baseBranch;
    private String branchPrefix;

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getBaseBranch() {
        return baseBranch;
    }

    public void setBaseBranch(String baseBranch) {
        this.baseBranch = baseBranch;
    }

    public String getBranchPrefix() {
        return branchPrefix;
    }

    public void setBranchPrefix(String branchPrefix) {
        this.branchPrefix = branchPrefix;
    }
}
```

This class keeps external configuration separate from business logic. The token
is readable by `GitHubClient`, but it is never exposed through a getter on an API
response DTO.

## 7. Step 3: Add a Reusable Java HttpClient Bean

File:

`spring-api/src/main/java/com/opslens/config/HttpClientConfig.java`

Complete code:

```java
package com.opslens.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
}
```

`@Bean` tells Spring to create and manage one `HttpClient` instance. Constructor
injection then gives that client to `GitHubClient`.

The connection timeout limits how long the client waits while establishing a
network connection. It is different from a complete request timeout, which is
set on each `HttpRequest` below.

## 8. Step 4: Represent a GitHub Reference

File:

`spring-api/src/main/java/com/opslens/github/GitHubBranchReference.java`

Complete code:

```java
package com.opslens.github;

public record GitHubBranchReference(
        String ref,
        String sha,
        String url
) {
}
```

A Java `record` is a concise immutable data carrier. Java automatically creates
the constructor and accessor methods:

```java
reference.ref();
reference.sha();
reference.url();
```

This is an internal integration model, not a JPA entity.

## 9. Step 5: Define a Safe GitHub API Exception

File:

`spring-api/src/main/java/com/opslens/github/GitHubApiException.java`

Complete code:

```java
package com.opslens.github;

public class GitHubApiException extends RuntimeException {

    private final int statusCode;

    public GitHubApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public GitHubApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
```

This exception preserves the GitHub HTTP status without exposing the token.
Status `0` means the request failed before a GitHub response was received.

## 10. Step 6: Implement GitHubClient

File:

`spring-api/src/main/java/com/opslens/github/GitHubClient.java`

Complete code:

```java
package com.opslens.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opslens.config.GitHubProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class GitHubClient {

    private static final String ACCEPT_HEADER =
            "application/vnd.github+json";
    private static final int MAX_ERROR_BODY_LENGTH = 1_000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GitHubProperties properties;

    public GitHubClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            GitHubProperties properties
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<GitHubBranchReference> findBranch(String branch) {
        validateConfiguration();
        validateBranch(branch);

        String ref = "heads/" + branch;
        URI uri = URI.create(repositoryApiUrl()
                + "/git/ref/"
                + encodePathValue(ref));

        HttpRequest request = requestBuilder(uri)
                .GET()
                .build();

        HttpResponse<String> response = send(request);

        if (response.statusCode() == 404) {
            return Optional.empty();
        }

        requireStatus(response, 200, "read GitHub branch");
        return Optional.of(parseReference(response.body()));
    }

    public GitHubBranchReference createBranch(
            String branch,
            String sha
    ) {
        validateConfiguration();
        validateBranch(branch);

        if (sha == null || sha.isBlank()) {
            throw new IllegalArgumentException(
                    "A base commit SHA is required."
            );
        }

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("ref", "refs/heads/" + branch);
        payload.put("sha", sha);

        HttpRequest request = requestBuilder(
                URI.create(repositoryApiUrl() + "/git/refs")
        )
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                .build();

        HttpResponse<String> response = send(request);
        requireStatus(response, 201, "create GitHub branch");
        return parseReference(response.body());
    }

    public String qualifiedRepository() {
        return properties.getOwner().trim()
                + "/"
                + properties.getRepository().trim();
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", ACCEPT_HEADER)
                .header(
                        "Authorization",
                        "Bearer " + properties.getToken().trim()
                )
                .header(
                        "X-GitHub-Api-Version",
                        properties.getApiVersion().trim()
                );
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (IOException error) {
            throw new GitHubApiException(
                    "GitHub request failed before a response was received.",
                    error
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new GitHubApiException(
                    "GitHub request was interrupted.",
                    error
            );
        }
    }

    private GitHubBranchReference parseReference(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String ref = root.path("ref").asText();
            String sha = root.path("object").path("sha").asText();
            String url = root.path("url").asText();

            if (ref.isBlank() || sha.isBlank()) {
                throw new GitHubApiException(
                        502,
                        "GitHub returned an invalid reference response."
                );
            }

            return new GitHubBranchReference(ref, sha, url);
        } catch (JsonProcessingException error) {
            throw new GitHubApiException(
                    "Could not parse the GitHub reference response.",
                    error
            );
        }
    }

    private void requireStatus(
            HttpResponse<String> response,
            int expectedStatus,
            String action
    ) {
        if (response.statusCode() == expectedStatus) {
            return;
        }

        throw new GitHubApiException(
                response.statusCode(),
                "Could not "
                        + action
                        + ". GitHub returned HTTP "
                        + response.statusCode()
                        + ": "
                        + safeErrorBody(response.body())
        );
    }

    private String repositoryApiUrl() {
        return removeTrailingSlash(properties.getApiBaseUrl())
                + "/repos/"
                + encodePathValue(properties.getOwner().trim())
                + "/"
                + encodePathValue(properties.getRepository().trim());
    }

    private String encodePathValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private String removeTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - 1
            );
        }

        return normalized;
    }

    private String toJson(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(
                    "Could not serialize the GitHub branch request.",
                    error
            );
        }
    }

    private void validateConfiguration() {
        if (properties.getToken() == null
                || properties.getToken().isBlank()) {
            throw new GitHubApiException(
                    0,
                    "GitHub token is not configured."
            );
        }

        if (properties.getApiBaseUrl() == null
                || properties.getApiBaseUrl().isBlank()
                || properties.getApiVersion() == null
                || properties.getApiVersion().isBlank()
                || properties.getOwner() == null
                || properties.getOwner().isBlank()
                || properties.getRepository() == null
                || properties.getRepository().isBlank()) {
            throw new GitHubApiException(
                    0,
                    "GitHub API configuration is incomplete."
            );
        }
    }

    private void validateBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            throw new IllegalArgumentException(
                    "GitHub branch name is required."
            );
        }

        if (branch.startsWith("/")
                || branch.endsWith("/")
                || branch.contains("..")) {
            throw new IllegalArgumentException(
                    "GitHub branch name is invalid: " + branch
            );
        }
    }

    private String safeErrorBody(String body) {
        if (body == null || body.isBlank()) {
            return "No response body";
        }

        String normalized = body.replaceAll("\\s+", " ").trim();

        if (normalized.length() <= MAX_ERROR_BODY_LENGTH) {
            return normalized;
        }

        return normalized.substring(0, MAX_ERROR_BODY_LENGTH)
                + "...";
    }
}
```

### Important client logic

`Authorization: Bearer ...` authenticates the request. The token is read only
while building the header and is never returned.

`X-GitHub-Api-Version` pins behavior to a documented API version instead of
implicitly accepting future breaking behavior.

`URLEncoder` escapes branch slashes and spaces for a URI path parameter.

`Optional.empty()` represents a missing branch after GitHub returns `404`. A
missing proposed branch is expected; a missing base branch is not.

`Thread.currentThread().interrupt()` restores the thread's interrupted status
before converting the checked exception into an application exception.

## 11. Step 7: Define Branch Creation Response

File:

`spring-api/src/main/java/com/opslens/dto/CreatePullRequestBranchResponse.java`

Complete code:

```java
package com.opslens.dto;

public class CreatePullRequestBranchResponse {

    private final Long patchSuggestionId;
    private final Long incidentId;
    private final String repository;
    private final String baseBranch;
    private final String branch;
    private final String baseCommitSha;
    private final String branchCommitSha;
    private final String status;
    private final boolean created;

    public CreatePullRequestBranchResponse(
            Long patchSuggestionId,
            Long incidentId,
            String repository,
            String baseBranch,
            String branch,
            String baseCommitSha,
            String branchCommitSha,
            String status,
            boolean created
    ) {
        this.patchSuggestionId = patchSuggestionId;
        this.incidentId = incidentId;
        this.repository = repository;
        this.baseBranch = baseBranch;
        this.branch = branch;
        this.baseCommitSha = baseCommitSha;
        this.branchCommitSha = branchCommitSha;
        this.status = status;
        this.created = created;
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

    public String getBaseBranch() {
        return baseBranch;
    }

    public String getBranch() {
        return branch;
    }

    public String getBaseCommitSha() {
        return baseCommitSha;
    }

    public String getBranchCommitSha() {
        return branchCommitSha;
    }

    public String getStatus() {
        return status;
    }

    public boolean isCreated() {
        return created;
    }
}
```

The response deliberately excludes the token and raw GitHub response body.

## 12. Step 8: Define Domain Exceptions

File:

`spring-api/src/main/java/com/opslens/service/PullRequestBlockedException.java`

```java
package com.opslens.service;

import java.util.List;

public class PullRequestBlockedException extends RuntimeException {

    private final List<String> blockers;

    public PullRequestBlockedException(List<String> blockers) {
        super("Pull request branch creation is blocked.");
        this.blockers = List.copyOf(blockers);
    }

    public List<String> getBlockers() {
        return blockers;
    }
}
```

File:

`spring-api/src/main/java/com/opslens/service/PullRequestBranchConflictException.java`

```java
package com.opslens.service;

public class PullRequestBranchConflictException
        extends RuntimeException {

    public PullRequestBranchConflictException(String message) {
        super(message);
    }
}
```

These exceptions represent business failures, not programming failures. The
controller maps them to HTTP `409 Conflict`.

## 13. Step 9: Implement PullRequestBranchService

File:

`spring-api/src/main/java/com/opslens/service/PullRequestBranchService.java`

Complete code:

```java
package com.opslens.service;

import com.opslens.dto.CreatePullRequestBranchResponse;
import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.github.GitHubApiException;
import com.opslens.github.GitHubBranchReference;
import com.opslens.github.GitHubClient;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PullRequestBranchService {

    private final PullRequestPreflightService preflightService;
    private final GitHubClient gitHubClient;

    public PullRequestBranchService(
            PullRequestPreflightService preflightService,
            GitHubClient gitHubClient
    ) {
        this.preflightService = preflightService;
        this.gitHubClient = gitHubClient;
    }

    public CreatePullRequestBranchResponse createBranch(
            Long patchSuggestionId
    ) {
        PullRequestPreflightResponse plan =
                preflightService.buildPlan(patchSuggestionId);

        if (!plan.isReady()) {
            throw new PullRequestBlockedException(plan.getBlockers());
        }

        GitHubBranchReference baseReference = gitHubClient
                .findBranch(plan.getBaseBranch())
                .orElseThrow(() -> new GitHubApiException(
                        404,
                        "GitHub base branch was not found: "
                                + plan.getBaseBranch()
                ));

        Optional<GitHubBranchReference> existing =
                gitHubClient.findBranch(plan.getProposedBranch());

        if (existing.isPresent()) {
            return resolveExistingBranch(
                    plan,
                    baseReference,
                    existing.get()
            );
        }

        try {
            GitHubBranchReference created = gitHubClient.createBranch(
                    plan.getProposedBranch(),
                    baseReference.sha()
            );

            return response(
                    plan,
                    baseReference,
                    created,
                    "CREATED",
                    true
            );
        } catch (GitHubApiException error) {
            if (error.getStatusCode() != 422) {
                throw error;
            }

            return resolveCreateRace(plan, baseReference, error);
        }
    }

    private CreatePullRequestBranchResponse resolveExistingBranch(
            PullRequestPreflightResponse plan,
            GitHubBranchReference baseReference,
            GitHubBranchReference existing
    ) {
        if (!baseReference.sha().equals(existing.sha())) {
            throw new PullRequestBranchConflictException(
                    "The proposed branch already exists at a different "
                            + "commit and will not be overwritten."
            );
        }

        return response(
                plan,
                baseReference,
                existing,
                "ALREADY_EXISTS",
                false
        );
    }

    private CreatePullRequestBranchResponse resolveCreateRace(
            PullRequestPreflightResponse plan,
            GitHubBranchReference baseReference,
            GitHubApiException originalError
    ) {
        Optional<GitHubBranchReference> existing =
                gitHubClient.findBranch(plan.getProposedBranch());

        if (existing.isEmpty()) {
            throw originalError;
        }

        return resolveExistingBranch(
                plan,
                baseReference,
                existing.get()
        );
    }

    private CreatePullRequestBranchResponse response(
            PullRequestPreflightResponse plan,
            GitHubBranchReference baseReference,
            GitHubBranchReference branchReference,
            String status,
            boolean created
    ) {
        return new CreatePullRequestBranchResponse(
                plan.getPatchSuggestionId(),
                plan.getIncidentId(),
                plan.getRepository(),
                plan.getBaseBranch(),
                plan.getProposedBranch(),
                baseReference.sha(),
                branchReference.sha(),
                status,
                created
        );
    }
}
```

### Why readiness is checked again

An earlier preflight response may be stale. A newer test may have failed, the
patch may have changed, or configuration may have changed. The external write
must be protected by a fresh verification.

### Idempotency

Idempotency means retrying the same request does not create an unsafe second
result.

```text
Branch missing
-> create it

Branch exists at expected base SHA
-> return ALREADY_EXISTS

Branch exists at another SHA
-> stop; never overwrite it
```

Two requests may both check that a branch is missing, then race to create it.
GitHub returns `422` to one request. The service reads the branch again and
accepts it only when it points to the expected SHA.

## 14. Step 10: Update PullRequestController

File:

`spring-api/src/main/java/com/opslens/controller/PullRequestController.java`

Complete code:

```java
package com.opslens.controller;

import com.opslens.dto.CreatePullRequestBranchResponse;
import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.github.GitHubApiException;
import com.opslens.service.PullRequestBlockedException;
import com.opslens.service.PullRequestBranchConflictException;
import com.opslens.service.PullRequestBranchService;
import com.opslens.service.PullRequestPreflightService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/patch-suggestions")
public class PullRequestController {

    private final PullRequestPreflightService preflightService;
    private final PullRequestBranchService branchService;

    public PullRequestController(
            PullRequestPreflightService preflightService,
            PullRequestBranchService branchService
    ) {
        this.preflightService = preflightService;
        this.branchService = branchService;
    }

    @GetMapping("/{id}/pull-request/preflight")
    public ResponseEntity<PullRequestPreflightResponse> preflight(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(preflightService.buildPlan(id));
    }

    @PostMapping("/{id}/pull-request/branch")
    public ResponseEntity<CreatePullRequestBranchResponse> createBranch(
            @PathVariable Long id
    ) {
        CreatePullRequestBranchResponse response =
                branchService.createBranch(id);

        if (response.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            IllegalArgumentException error
    ) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                error.getMessage(),
                null
        );
    }

    @ExceptionHandler(PullRequestBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleBlocked(
            PullRequestBlockedException error
    ) {
        return errorResponse(
                HttpStatus.CONFLICT,
                error.getMessage(),
                error.getBlockers()
        );
    }

    @ExceptionHandler(PullRequestBranchConflictException.class)
    public ResponseEntity<Map<String, Object>> handleBranchConflict(
            PullRequestBranchConflictException error
    ) {
        return errorResponse(
                HttpStatus.CONFLICT,
                error.getMessage(),
                null
        );
    }

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<Map<String, Object>> handleGitHubError(
            GitHubApiException error
    ) {
        return errorResponse(
                HttpStatus.BAD_GATEWAY,
                error.getMessage(),
                null
        );
    }

    private ResponseEntity<Map<String, Object>> errorResponse(
            HttpStatus status,
            String message,
            Object blockers
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);

        if (blockers != null) {
            body.put("blockers", blockers);
        }

        return ResponseEntity.status(status).body(body);
    }
}
```

`GET` remains read-only. `POST` is required for branch creation because it
changes external GitHub state.

`201 Created` means OpsLens created a new branch. `200 OK` with
`ALREADY_EXISTS` means a retry found the expected branch. `409 Conflict` means a
safety rule blocked the action. `502 Bad Gateway` means the downstream GitHub
API failed.

## 15. Step 11: Unit Test the Branch Service

File:

`spring-api/src/test/java/com/opslens/service/PullRequestBranchServiceTests.java`

Complete code:

```java
package com.opslens.service;

import com.opslens.dto.CreatePullRequestBranchResponse;
import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.github.GitHubBranchReference;
import com.opslens.github.GitHubClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PullRequestBranchServiceTests {

    @Mock
    private PullRequestPreflightService preflightService;

    @Mock
    private GitHubClient gitHubClient;

    private PullRequestBranchService branchService;

    @BeforeEach
    void setUp() {
        branchService = new PullRequestBranchService(
                preflightService,
                gitHubClient
        );
    }

    @Test
    void createsBranchFromBaseShaWhenPreflightIsReady() {
        PullRequestPreflightResponse plan = readyPlan();
        GitHubBranchReference base = reference(
                "refs/heads/main",
                "base-sha"
        );
        GitHubBranchReference created = reference(
                "refs/heads/ai-fix/inc-15-patch-20",
                "base-sha"
        );

        when(preflightService.buildPlan(20L)).thenReturn(plan);
        when(gitHubClient.findBranch("main"))
                .thenReturn(Optional.of(base));
        when(gitHubClient.findBranch(
                "ai-fix/inc-15-patch-20"
        )).thenReturn(Optional.empty());
        when(gitHubClient.createBranch(
                "ai-fix/inc-15-patch-20",
                "base-sha"
        )).thenReturn(created);

        CreatePullRequestBranchResponse response =
                branchService.createBranch(20L);

        assertTrue(response.isCreated());
        assertEquals("CREATED", response.getStatus());
        assertEquals("base-sha", response.getBranchCommitSha());
    }

    @Test
    void blocksGitHubCallsWhenPreflightIsNotReady() {
        when(preflightService.buildPlan(20L))
                .thenReturn(blockedPlan());

        assertThrows(
                PullRequestBlockedException.class,
                () -> branchService.createBranch(20L)
        );

        verifyNoInteractions(gitHubClient);
    }

    @Test
    void returnsAlreadyExistsWhenBranchHasExpectedSha() {
        PullRequestPreflightResponse plan = readyPlan();
        GitHubBranchReference base = reference(
                "refs/heads/main",
                "base-sha"
        );
        GitHubBranchReference existing = reference(
                "refs/heads/ai-fix/inc-15-patch-20",
                "base-sha"
        );

        when(preflightService.buildPlan(20L)).thenReturn(plan);
        when(gitHubClient.findBranch("main"))
                .thenReturn(Optional.of(base));
        when(gitHubClient.findBranch(
                "ai-fix/inc-15-patch-20"
        )).thenReturn(Optional.of(existing));

        CreatePullRequestBranchResponse response =
                branchService.createBranch(20L);

        assertFalse(response.isCreated());
        assertEquals("ALREADY_EXISTS", response.getStatus());
        verify(gitHubClient, never()).createBranch(
                "ai-fix/inc-15-patch-20",
                "base-sha"
        );
    }

    @Test
    void blocksExistingBranchAtDifferentSha() {
        PullRequestPreflightResponse plan = readyPlan();

        when(preflightService.buildPlan(20L)).thenReturn(plan);
        when(gitHubClient.findBranch("main"))
                .thenReturn(Optional.of(reference(
                        "refs/heads/main",
                        "base-sha"
                )));
        when(gitHubClient.findBranch(
                "ai-fix/inc-15-patch-20"
        )).thenReturn(Optional.of(reference(
                "refs/heads/ai-fix/inc-15-patch-20",
                "different-sha"
        )));

        assertThrows(
                PullRequestBranchConflictException.class,
                () -> branchService.createBranch(20L)
        );
    }

    private PullRequestPreflightResponse readyPlan() {
        return new PullRequestPreflightResponse(
                20L,
                15L,
                "READY_FOR_PR",
                true,
                "Jake6654/sketch-my-day",
                "main",
                "ai-fix/inc-15-patch-20",
                "[OpsLens] Fix incident #15",
                "Pull request body",
                true,
                true,
                15L,
                "PASSED",
                true,
                List.of()
        );
    }

    private PullRequestPreflightResponse blockedPlan() {
        return new PullRequestPreflightResponse(
                20L,
                15L,
                "TESTS_FAILED",
                false,
                "Jake6654/sketch-my-day",
                "main",
                "ai-fix/inc-15-patch-20",
                "[OpsLens] Fix incident #15",
                "Pull request body",
                true,
                false,
                15L,
                "FAILED",
                true,
                List.of("The latest test run must pass.")
        );
    }

    private GitHubBranchReference reference(
            String ref,
            String sha
    ) {
        return new GitHubBranchReference(
                ref,
                sha,
                "https://api.github.com/reference"
        );
    }
}
```

The most important safety assertion is `verifyNoInteractions(gitHubClient)`.
It proves that a blocked preflight cannot accidentally call GitHub.

## 16. Step 12: Test the Integration Safely

First verify the token without printing it:

```bash
docker compose exec backend sh -lc \
  'test -n "$GITHUB_TOKEN" && echo configured || echo missing'
```

Rebuild the backend after adding the files:

```bash
cd /Users/jake/Documents/my-project/opslens
docker compose up -d --build backend
```

Check preflight one more time:

```bash
curl \
  http://localhost:8081/patch-suggestions/20/pull-request/preflight \
  | jq
```

Only continue when it returns:

```json
{
  "status": "READY_FOR_PR",
  "ready": true,
  "blockers": []
}
```

Create the branch:

```bash
curl -i -X POST \
  http://localhost:8081/patch-suggestions/20/pull-request/branch
```

Expected first response:

```json
{
  "patchSuggestionId": 20,
  "incidentId": 15,
  "repository": "Jake6654/sketch-my-day",
  "baseBranch": "main",
  "branch": "ai-fix/inc-15-patch-20",
  "baseCommitSha": "...",
  "branchCommitSha": "...",
  "status": "CREATED",
  "created": true
}
```

Run the same request again. The safe idempotent response should be:

```json
{
  "status": "ALREADY_EXISTS",
  "created": false
}
```

The two SHA values should match because Phase 5B creates the branch before
adding the patch commit.

## 17. Common Failure Results

`401 Unauthorized` means the token is invalid or expired.

`403 Forbidden` usually means the token lacks permission, repository policy
blocked the action, or GitHub rate limiting applied.

`404 Not Found` can mean the repository/base branch is wrong. GitHub may also
return `404` for a private resource when authentication is insufficient.

`409 Conflict` from OpsLens means preflight was blocked or the branch already
exists at a different commit.

`422 Unprocessable Entity` commonly appears when a ref already exists or the
reference request is invalid. The branch service rechecks GitHub before deciding
whether the request is a safe retry.

## 18. Why This Design Is Split Across Files

```text
GitHubProperties
-> stores typed external configuration

HttpClientConfig
-> creates reusable HTTP infrastructure

GitHubClient
-> knows GitHub HTTP endpoints and JSON

GitHubBranchReference
-> represents an external Git reference

PullRequestPreflightService
-> decides whether GitHub mutation is allowed

PullRequestBranchService
-> coordinates safe, idempotent branch creation

PullRequestController
-> maps HTTP requests and responses

CreatePullRequestBranchResponse
-> defines the public response contract

Service tests
-> prove that unsafe states never call GitHub
```

This follows the Single Responsibility Principle. AI logic remains in FastAPI;
deterministic GitHub state changes remain in Spring Boot.

## 19. Phase Boundary

After Phase 5B:

```text
main                           -> base SHA
ai-fix/inc-15-patch-20         -> same base SHA
```

No fix is present on the new branch yet.

The next steps are:

```text
Phase 5C -> create a Git commit containing only the validated patch
Phase 5D -> open and persist the pull request
Phase 5E -> show branch and PR status in the dashboard
Phase 5F -> verify the complete safety workflow
```
