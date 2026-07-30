# Phase 5A: Pull Request Preflight and Plan

This guide combines the complete Phase 5A implementation, the reason each file
exists, the end-to-end code flow, important Java concepts, and the test timeout
problem that appeared during final verification.

## 1. Goal of Phase 5A

Phase 5A prepares a pull request without changing GitHub.

It answers two questions:

1. Is this patch safe enough to become a pull request?
2. If it is safe, what repository, branch, title, and body should OpsLens use?

Phase 5A does not:

- create a GitHub branch;
- create a commit;
- push code;
- open a pull request; or
- write a pull request record to PostgreSQL.

It is a read-only safety gate between Phase 4 validation and Phase 5 GitHub
mutation.

```text
PatchSuggestion
    + latest TestRunResult
    + PatchVerificationResponse
    + GitHub configuration
                |
                v
PullRequestPreflightResponse
```

## 2. End-to-End Workflow

```text
GET /patch-suggestions/{id}/pull-request/preflight
        |
        v
PullRequestController
        |
        v
PullRequestPreflightService.buildPlan(id)
        |
        |-- Load PatchSuggestion
        |-- Reuse PatchVerificationService
        |-- Load Incident
        |-- Copy Phase 4 blockers
        |-- Validate GitHub configuration
        |-- Generate a safe branch name
        |-- Generate the pull request title and body
        |-- Calculate final readiness
        |
        v
PullRequestPreflightResponse
        |
        v
JSON response
```

The service reuses `PatchVerificationService` as the single source of truth for
Phase 4 safety rules. It does not implement patch and test validation a second
time.

## 3. Readiness Rules

A patch is ready only when all of the following conditions are true:

- the patch suggestion exists;
- the related incident exists;
- `patchValid` is `true`;
- `suggestedDiff` is not blank;
- the patch risk is not `HIGH`;
- a test run exists;
- the latest test run has `passed == true`;
- the latest test status is `PASSED`;
- the GitHub owner is configured;
- the GitHub repository is configured;
- the base branch is configured;
- the generated branch prefix is configured; and
- the proposed head branch is not a protected branch.

`requiresHumanReview == true` does not block PR creation. It means OpsLens may
prepare a PR, but a developer must review and merge it.

## 4. Step 1: Add GitHub Target Configuration

File:

`spring-api/src/main/resources/application.yml`

Complete configuration:

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
  owner: ${GITHUB_OWNER:Jake6654}
  repository: ${GITHUB_REPOSITORY:sketch-my-day}
  base-branch: ${GITHUB_BASE_BRANCH:main}
  branch-prefix: ${GITHUB_BRANCH_PREFIX:ai-fix}
```

The syntax `${NAME:default}` means:

```text
Use the NAME environment variable if it exists.
Otherwise, use the value after the colon.
```

For example:

```yaml
owner: ${GITHUB_OWNER:Jake6654}
```

uses `GITHUB_OWNER` when configured and `Jake6654` otherwise.

This is suitable for the current single-project prototype. A future SaaS
version should load these values from a per-project `RepositoryConnection`
record instead of global application configuration.

No GitHub token is required in Phase 5A because this phase does not call a
write API.

## 5. Step 2: Bind Configuration to a Java Object

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

    private String owner;
    private String repository;
    private String baseBranch;
    private String branchPrefix;

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

### Why this file exists

This class converts untyped YAML values into a typed Java dependency.

Without it, services would repeatedly use:

```java
@Value("${github.owner}")
```

With it, a service receives one configuration object:

```java
private final GitHubProperties githubProperties;
```

### Important annotations

`@Component` registers the class as a Spring bean. Spring can then inject it
through a constructor.

`@ConfigurationProperties(prefix = "github")` maps:

```text
github.owner         -> owner
github.repository    -> repository
github.base-branch   -> baseBranch
github.branch-prefix -> branchPrefix
```

Spring Boot automatically maps kebab-case YAML keys such as `base-branch` to
camelCase Java fields such as `baseBranch`.

The setters allow Spring Boot to assign the external configuration values after
constructing the object. The getters allow business services to read them.

## 6. Step 3: Define the API Response DTO

File:

`spring-api/src/main/java/com/opslens/dto/PullRequestPreflightResponse.java`

Complete code:

```java
package com.opslens.dto;

import java.util.List;

public class PullRequestPreflightResponse {

    private final Long patchSuggestionId;
    private final Long incidentId;
    private final String status;
    private final boolean ready;
    private final String repository;
    private final String baseBranch;
    private final String proposedBranch;
    private final String title;
    private final String body;
    private final Boolean patchValid;
    private final Boolean testsPassed;
    private final Long latestTestRunId;
    private final String latestTestStatus;
    private final boolean requiresHumanReview;
    private final List<String> blockers;

    public PullRequestPreflightResponse(
            Long patchSuggestionId,
            Long incidentId,
            String status,
            boolean ready,
            String repository,
            String baseBranch,
            String proposedBranch,
            String title,
            String body,
            Boolean patchValid,
            Boolean testsPassed,
            Long latestTestRunId,
            String latestTestStatus,
            boolean requiresHumanReview,
            List<String> blockers
    ) {
        this.patchSuggestionId = patchSuggestionId;
        this.incidentId = incidentId;
        this.status = status;
        this.ready = ready;
        this.repository = repository;
        this.baseBranch = baseBranch;
        this.proposedBranch = proposedBranch;
        this.title = title;
        this.body = body;
        this.patchValid = patchValid;
        this.testsPassed = testsPassed;
        this.latestTestRunId = latestTestRunId;
        this.latestTestStatus = latestTestStatus;
        this.requiresHumanReview = requiresHumanReview;
        this.blockers = List.copyOf(blockers);
    }

    public Long getPatchSuggestionId() {
        return patchSuggestionId;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getStatus() {
        return status;
    }

    public boolean isReady() {
        return ready;
    }

    public String getRepository() {
        return repository;
    }

    public String getBaseBranch() {
        return baseBranch;
    }

    public String getProposedBranch() {
        return proposedBranch;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Boolean getPatchValid() {
        return patchValid;
    }

    public Boolean getTestsPassed() {
        return testsPassed;
    }

    public Long getLatestTestRunId() {
        return latestTestRunId;
    }

    public String getLatestTestStatus() {
        return latestTestStatus;
    }

    public boolean isRequiresHumanReview() {
        return requiresHumanReview;
    }

    public List<String> getBlockers() {
        return blockers;
    }
}
```

### Why this file exists

This DTO defines the stable JSON contract returned to the frontend.

It is not an `@Entity` because a preflight response is derived from existing
data. No GitHub resource has been created yet, so there is no external PR state
to persist.

### Why the fields are final

`final` prevents the response from being modified after its constructor
finishes. This makes the response predictable and closer to an immutable value
object.

### Why List.copyOf is used

`List.copyOf(blockers)` creates an unmodifiable copy. A caller cannot mutate the
response later by changing the original list.

### boolean versus Boolean

`ready` always has a definite answer, so it uses primitive `boolean`.

`testsPassed` can be:

```text
true  -> tests passed
false -> tests ran and failed
null  -> tests have not run
```

It therefore uses nullable `Boolean`.

## 7. Step 4: Implement the Preflight Business Logic

File:

`spring-api/src/main/java/com/opslens/service/PullRequestPreflightService.java`

Complete recommended code:

```java
package com.opslens.service;

import com.opslens.config.GitHubProperties;
import com.opslens.dto.PatchVerificationResponse;
import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.model.Incident;
import com.opslens.model.PatchSuggestion;
import com.opslens.repository.IncidentRepository;
import com.opslens.repository.PatchSuggestionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PullRequestPreflightService {

    private static final int MAX_TITLE_LENGTH = 100;

    private final PatchVerificationService patchVerificationService;
    private final PatchSuggestionRepository patchSuggestionRepository;
    private final IncidentRepository incidentRepository;
    private final GitHubProperties githubProperties;

    public PullRequestPreflightService(
            PatchVerificationService patchVerificationService,
            PatchSuggestionRepository patchSuggestionRepository,
            IncidentRepository incidentRepository,
            GitHubProperties githubProperties
    ) {
        this.patchVerificationService = patchVerificationService;
        this.patchSuggestionRepository = patchSuggestionRepository;
        this.incidentRepository = incidentRepository;
        this.githubProperties = githubProperties;
    }

    public PullRequestPreflightResponse buildPlan(Long patchSuggestionId) {
        PatchSuggestion patch = patchSuggestionRepository
                .findById(patchSuggestionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Patch suggestion not found: " + patchSuggestionId
                ));

        PatchVerificationResponse verification =
                patchVerificationService.verify(patchSuggestionId);

        Incident incident = incidentRepository
                .findById(patch.getIncidentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Incident not found: " + patch.getIncidentId()
                ));

        List<String> blockers =
                new ArrayList<>(verification.getBlockers());

        String owner = normalizeValue(githubProperties.getOwner());
        String repositoryName =
                normalizeValue(githubProperties.getRepository());
        String baseBranch =
                normalizeValue(githubProperties.getBaseBranch());
        String branchPrefix =
                normalizeBranchPrefix(githubProperties.getBranchPrefix());

        validateConfiguration(
                owner,
                repositoryName,
                baseBranch,
                branchPrefix,
                blockers
        );

        String qualifiedRepository =
                buildQualifiedRepository(owner, repositoryName);

        String proposedBranch = buildBranchName(
                branchPrefix,
                patch.getIncidentId(),
                patchSuggestionId
        );

        if (isProtectedBranch(proposedBranch)) {
            blockers.add(
                    "The proposed branch must not be a protected branch."
            );
        }

        String title = buildTitle(patch.getIncidentId(), incident);
        String body = buildBody(
                incident,
                patch,
                verification,
                proposedBranch
        );

        boolean ready =
                verification.isReadyForPullRequest()
                        && blockers.isEmpty();

        String status;

        if (ready) {
            status = "READY_FOR_PR";
        } else if (verification.isReadyForPullRequest()) {
            status = "CONFIGURATION_BLOCKED";
        } else {
            status = verification.getStatus();
        }

        return new PullRequestPreflightResponse(
                patchSuggestionId,
                patch.getIncidentId(),
                status,
                ready,
                qualifiedRepository,
                baseBranch,
                proposedBranch,
                title,
                body,
                verification.getPatchValid(),
                verification.getTestsPassed(),
                verification.getLatestTestRunId(),
                verification.getLatestTestStatus(),
                verification.isRequiresHumanReview(),
                blockers
        );
    }

    private void validateConfiguration(
            String owner,
            String repository,
            String baseBranch,
            String branchPrefix,
            List<String> blockers
    ) {
        if (owner.isBlank()) {
            blockers.add("GitHub owner is not configured.");
        }

        if (repository.isBlank()) {
            blockers.add("GitHub repository is not configured.");
        }

        if (baseBranch.isBlank()) {
            blockers.add("GitHub base branch is not configured.");
        }

        if (branchPrefix.isBlank()) {
            blockers.add("GitHub branch prefix is not configured.");
        }
    }

    private String buildQualifiedRepository(
            String owner,
            String repository
    ) {
        if (owner.isBlank() || repository.isBlank()) {
            return "";
        }

        return owner + "/" + repository;
    }

    private String buildBranchName(
            String branchPrefix,
            Long incidentId,
            Long patchSuggestionId
    ) {
        if (branchPrefix.isBlank()) {
            return "";
        }

        return branchPrefix
                + "/inc-"
                + incidentId
                + "-patch-"
                + patchSuggestionId;
    }

    private String buildTitle(
            Long incidentId,
            Incident incident
    ) {
        String incidentTitle = normalizeSingleLine(incident.getTitle());

        String title = "[OpsLens] Fix incident #"
                + incidentId
                + ": "
                + incidentTitle;

        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }

        return title.substring(0, MAX_TITLE_LENGTH);
    }

    private String buildBody(
            Incident incident,
            PatchSuggestion patch,
            PatchVerificationResponse verification,
            String proposedBranch
    ) {
        StringBuilder body = new StringBuilder();

        body.append("## Incident\n\n")
                .append("- Incident: #")
                .append(patch.getIncidentId())
                .append("\n")
                .append("- Patch suggestion: #")
                .append(patch.getId())
                .append("\n")
                .append("- Project: ")
                .append(safeValue(incident.getProject()))
                .append("\n")
                .append("- Service: ")
                .append(safeValue(incident.getService()))
                .append("\n")
                .append("- Severity: ")
                .append(safeValue(incident.getSeverity()))
                .append("\n")
                .append("- Proposed branch: ")
                .append(safeValue(proposedBranch))
                .append("\n\n");

        body.append("## Root Cause\n\n")
                .append(safeValue(patch.getRootCause()))
                .append("\n\n");

        body.append("## Proposed Fix\n\n")
                .append(safeValue(patch.getPatchSummary()))
                .append("\n\n");

        body.append("## Validation\n\n")
                .append("- Patch validation: ")
                .append(Boolean.TRUE.equals(verification.getPatchValid())
                        ? "Passed"
                        : "Failed")
                .append("\n")
                .append("- Test run: ")
                .append(verification.getLatestTestRunId() == null
                        ? "Not available"
                        : "#" + verification.getLatestTestRunId())
                .append("\n")
                .append("- Test status: ")
                .append(safeValue(verification.getLatestTestStatus()))
                .append("\n\n");

        body.append("## Safety\n\n")
                .append("This pull request was generated by OpsLens ")
                .append("and requires human review before merging.\n");

        return body.toString();
    }

    private String normalizeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeSingleLine(String value) {
        if (value == null || value.isBlank()) {
            return "Untitled incident";
        }

        return value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeBranchPrefix(String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._/-]+", "-")
                .replaceAll("/{2,}", "/")
                .replaceAll("^[./-]+|[./-]+$", "");
    }

    private boolean isProtectedBranch(String branch) {
        return "main".equalsIgnoreCase(branch)
                || "master".equalsIgnoreCase(branch)
                || "production".equalsIgnoreCase(branch)
                || "release".equalsIgnoreCase(branch);
    }

    private String safeValue(String value) {
        return value == null || value.isBlank()
                ? "Not available"
                : value;
    }
}
```

### Why this class exists

The controller should handle HTTP concerns. The service should handle business
rules. The repositories should handle persistence.

```text
Controller -> HTTP boundary
Service    -> business decisions
Repository -> database access
```

### Loading the patch

`findById` returns an `Optional<PatchSuggestion>`. `orElseThrow` turns an empty
result into a clear domain error.

### Reusing Phase 4 verification

The preflight service calls:

```java
patchVerificationService.verify(patchSuggestionId);
```

It does not reimplement patch validation, risk checks, and test checks. If two
services duplicated those rules, they could disagree about readiness.

### Copying blockers

`verification.getBlockers()` is immutable. The service creates a mutable copy:

```java
new ArrayList<>(verification.getBlockers())
```

It can then add GitHub configuration blockers without changing the Phase 4
response.

### Collecting errors instead of failing immediately

Missing configuration adds messages to `blockers`. This allows the caller to
see every problem in one response instead of fixing them one at a time.

### Branch naming

The generated branch is deterministic:

```text
ai-fix/inc-15-patch-20
```

It identifies the incident and patch suggestion without querying the database.

### Regular expressions used for branch normalization

```java
.replaceAll("[^a-z0-9._/-]+", "-")
```

replaces unsupported character groups with `-`.

```java
.replaceAll("/{2,}", "/")
```

collapses repeated slashes.

```java
.replaceAll("^[./-]+|[./-]+$", "")
```

removes unsafe punctuation from the beginning and end.

`Locale.ROOT` makes lowercase conversion deterministic across host languages.

### Protected branches

The base branch may be `main`; that is the PR target. The generated head branch
must not be `main`, `master`, `production`, or `release`.

### Building the PR body

`StringBuilder` efficiently assembles a multi-section Markdown document.
`String` is immutable, so repeated string concatenation creates unnecessary
intermediate objects.

The body intentionally excludes secrets, environment variables, raw request
bodies, and full test output.

### Final readiness

```java
boolean ready =
        verification.isReadyForPullRequest()
                && blockers.isEmpty();
```

Both Phase 4 validation and Phase 5A configuration must pass.

## 8. Step 5: Expose the Preflight Endpoint

File:

`spring-api/src/main/java/com/opslens/controller/PullRequestController.java`

Complete recommended code:

```java
package com.opslens.controller;

import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.service.PullRequestPreflightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/patch-suggestions")
public class PullRequestController {

    private final PullRequestPreflightService preflightService;

    public PullRequestController(
            PullRequestPreflightService preflightService
    ) {
        this.preflightService = preflightService;
    }

    @GetMapping("/{id}/pull-request/preflight")
    public ResponseEntity<PullRequestPreflightResponse> preflight(
            @PathVariable Long id
    ) {
        try {
            PullRequestPreflightResponse response =
                    preflightService.buildPlan(id);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException error) {
            return ResponseEntity.notFound().build();
        }
    }
}
```

`@RestController` marks the class as an HTTP controller whose return values are
serialized to JSON.

`@RequestMapping("/patch-suggestions")` defines the shared URL prefix.

`@GetMapping("/{id}/pull-request/preflight")` completes this endpoint:

```text
GET /patch-suggestions/20/pull-request/preflight
```

`@PathVariable Long id` converts `20` from the URL into a Java `Long`.

`GET` is correct because this endpoint does not mutate OpsLens or GitHub.

The current project version temporarily contains two fields for the same
`PullRequestPreflightService`. Only the single dependency shown above is
necessary.

## 9. Step 6: Add Unit Tests

Recommended filename:

`spring-api/src/test/java/com/opslens/service/PullRequestPreflightServiceTests.java`

Complete code:

```java
package com.opslens.service;

import com.opslens.config.GitHubProperties;
import com.opslens.dto.PatchVerificationResponse;
import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.model.Incident;
import com.opslens.model.PatchSuggestion;
import com.opslens.repository.IncidentRepository;
import com.opslens.repository.PatchSuggestionRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PullRequestPreflightServiceTests {

    @Mock
    private PatchVerificationService patchVerificationService;

    @Mock
    private PatchSuggestionRepository patchSuggestionRepository;

    @Mock
    private IncidentRepository incidentRepository;

    private GitHubProperties githubProperties;
    private PullRequestPreflightService preflightService;

    @BeforeEach
    void setUp() {
        githubProperties = new GitHubProperties();
        githubProperties.setOwner("Jake6654");
        githubProperties.setRepository("sketch-my-day");
        githubProperties.setBaseBranch("main");
        githubProperties.setBranchPrefix("ai-fix");

        preflightService = new PullRequestPreflightService(
                patchVerificationService,
                patchSuggestionRepository,
                incidentRepository,
                githubProperties
        );
    }

    @Test
    void createsReadyPlanWhenPatchAndTestsPass() {
        PatchSuggestion patch = createPatch();
        Incident incident = createIncident();

        when(patchSuggestionRepository.findById(21L))
                .thenReturn(Optional.of(patch));

        when(patchVerificationService.verify(21L))
                .thenReturn(readyVerification());

        when(incidentRepository.findById(15L))
                .thenReturn(Optional.of(incident));

        PullRequestPreflightResponse response =
                preflightService.buildPlan(21L);

        assertTrue(response.isReady());
        assertEquals("READY_FOR_PR", response.getStatus());
        assertEquals(
                "Jake6654/sketch-my-day",
                response.getRepository()
        );
        assertEquals(
                "ai-fix/inc-15-patch-21",
                response.getProposedBranch()
        );
        assertTrue(response.getBlockers().isEmpty());
    }

    @Test
    void blocksPlanWhenVerificationFails() {
        PatchSuggestion patch = createPatch();
        Incident incident = createIncident();

        when(patchSuggestionRepository.findById(21L))
                .thenReturn(Optional.of(patch));

        when(patchVerificationService.verify(21L))
                .thenReturn(blockedVerification());

        when(incidentRepository.findById(15L))
                .thenReturn(Optional.of(incident));

        PullRequestPreflightResponse response =
                preflightService.buildPlan(21L);

        assertFalse(response.isReady());
        assertEquals("TESTS_FAILED", response.getStatus());
        assertEquals(1, response.getBlockers().size());
    }

    @Test
    void blocksPlanWhenGitHubOwnerIsMissing() {
        githubProperties.setOwner("");

        PatchSuggestion patch = createPatch();
        Incident incident = createIncident();

        when(patchSuggestionRepository.findById(21L))
                .thenReturn(Optional.of(patch));

        when(patchVerificationService.verify(21L))
                .thenReturn(readyVerification());

        when(incidentRepository.findById(15L))
                .thenReturn(Optional.of(incident));

        PullRequestPreflightResponse response =
                preflightService.buildPlan(21L);

        assertFalse(response.isReady());
        assertEquals(
                "CONFIGURATION_BLOCKED",
                response.getStatus()
        );
        assertTrue(response.getBlockers().contains(
                "GitHub owner is not configured."
        ));
    }

    @Test
    void throwsWhenPatchSuggestionDoesNotExist() {
        when(patchSuggestionRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> preflightService.buildPlan(999L)
        );
    }

    private PatchSuggestion createPatch() {
        return new PatchSuggestion(
                15L,
                "A required field was null.",
                "Add request validation.",
                "--- a/File.java\n+++ b/File.java",
                "LOW",
                true,
                true,
                "Patch is valid."
        );
    }

    private Incident createIncident() {
        return new Incident(
                10L,
                "sketch-my-day",
                "dev",
                "DiaryService",
                "ERROR",
                "OPEN",
                "Diary save validation failure"
        );
    }

    private PatchVerificationResponse readyVerification() {
        return new PatchVerificationResponse(
                21L,
                15L,
                "READY_FOR_PR",
                true,
                true,
                true,
                31L,
                "PASSED",
                true,
                List.of()
        );
    }

    private PatchVerificationResponse blockedVerification() {
        return new PatchVerificationResponse(
                21L,
                15L,
                "TESTS_FAILED",
                false,
                true,
                false,
                31L,
                "FAILED",
                true,
                List.of("The latest test run must pass.")
        );
    }
}
```

`@ExtendWith(MockitoExtension.class)` enables Mockito integration with JUnit 5.

`@Mock` creates fake dependencies, so these tests do not require PostgreSQL.

`when(...).thenReturn(...)` defines what a fake dependency returns for a
specific method call.

The tests cover:

- a ready patch;
- a Phase 4 verification failure;
- missing GitHub configuration; and
- a missing patch suggestion.

The current filename contains the typo `PullReqeust`. Renaming it to
`PullRequestPreflightServiceTests.java` makes the filename match the class.

## 10. Step 7: Build and Call the Endpoint

Run Spring tests:

```bash
cd /Users/jake/Documents/my-project/opslens/spring-api
./gradlew test
```

Rebuild the backend:

```bash
cd /Users/jake/Documents/my-project/opslens
docker compose up -d --build backend
```

Call preflight:

```bash
curl \
  http://localhost:8081/patch-suggestions/20/pull-request/preflight \
  | jq
```

Before tests ran, the response correctly reported:

```json
{
  "status": "TESTS_NOT_RUN",
  "ready": false,
  "patchValid": true,
  "testsPassed": null,
  "blockers": [
    "Tests have not been run for this patch."
  ]
}
```

This was a successful preflight result. `ready: false` was not an API failure;
it was a safety decision.

## 11. The Timeout Found During Final Verification

After preflight reported `TESTS_NOT_RUN`, OpsLens ran:

```bash
curl -X POST \
  http://localhost:8081/patch-suggestions/20/run-tests \
  | jq
```

The patch passed `git apply`, Java compilation reached `testClasses`, but the
test did not finish within 120 seconds.

```text
Test command timed out after 120 seconds.
```

OpsLens stored the failed test result. The next preflight correctly returned:

```text
PR BLOCKED: TESTS_FAILED
The latest test run must pass.
```

The UI and safety gate were working correctly. The problem was the target
project's test environment.

## 12. Why @SpringBootTest Needed a Database

The target project had:

```java
@SpringBootTest
class SketchMyDayApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

`@SpringBootTest` starts almost the complete Spring application context:

```text
Read configuration
-> create controllers
-> create services
-> create repositories
-> configure DataSource
-> initialize JPA
-> create HTTP clients
-> finish application startup
```

The production `application.yml` requires PostgreSQL settings:

```text
DB_URL
DB_USER
DB_PASSWORD
```

The isolated test runner intentionally does not receive production database
credentials. Without a separate test profile, application startup can wait for
missing or unreachable infrastructure.

## 13. H2: The Isolated Test Database

H2 is a lightweight relational database implemented in Java. It supports
tables, rows, SQL, primary keys, foreign keys, transactions, and JDBC.

Unlike PostgreSQL, H2 can run in the same JVM memory as the test:

```text
Test starts
-> H2 database is created in memory
-> JPA creates tables
-> tests run
-> test finishes
-> H2 database disappears
```

Production still uses PostgreSQL or Supabase. H2 is a test-only dependency:

```gradle
testRuntimeOnly 'com.h2database:h2'
```

H2 is appropriate for context-loading and basic repository tests, but it is not
a perfect PostgreSQL replacement. PostgreSQL-specific behavior should later be
verified with Testcontainers.

## 14. Complete Test Profile Changes in sketch-my-day

### build.gradle

File:

`sketch-my-day/backend/build.gradle`

Complete current code:

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.3'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'sketch-my-day'
version = '0.0.1-SNAPSHOT'
description = 'Demo project for Spring Boot'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    compileOnly 'org.projectlombok:lombok'
    runtimeOnly 'org.postgresql:postgresql'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-validation-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testRuntimeOnly 'com.h2database:h2'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

### application-test.yml

File:

`sketch-my-day/backend/src/test/resources/application-test.yml`

Complete code:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:sketchmyday;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
    open-in-view: false

app:
  debug-failures:
    enabled: true

ai:
  service:
    base-url: http://127.0.0.1:9999

opslens:
  url: http://127.0.0.1:9998
  api-key: ""
  project: sketch-my-day
  environment: test
```

`MODE=PostgreSQL` makes some H2 behavior closer to PostgreSQL.

`DB_CLOSE_DELAY=-1` keeps the in-memory database alive for the lifetime of the
JVM instead of closing it when one connection closes.

`ddl-auto: create-drop` creates the test schema on startup and removes it after
the test context closes.

Fake local URLs allow HTTP client beans to be created without contacting real
external services during `contextLoads`.

### SketchMyDayApplicationTests.java

File:

`sketch-my-day/backend/src/test/java/sketch_my_day/demo/SketchMyDayApplicationTests.java`

Complete code:

```java
package sketch_my_day.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SketchMyDayApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

`@ActiveProfiles("test")` tells Spring Boot to load
`application-test.yml` in addition to the base configuration.

## 15. What the Gradle Daemon Is

Gradle can keep a Java process running in the background after a build. This is
the Gradle daemon.

For local development, the daemon improves repeated build speed:

```text
First build -> start daemon -> compile and test
Next build  -> reuse daemon -> compile and test faster
```

OpsLens uses disposable temporary workspaces:

```text
Patch A -> workspace A -> test -> delete
Patch B -> workspace B -> test -> delete
```

A persistent daemon can retain memory, consume CPU, outlive a temporary test,
or be shared across patch runs. OpsLens therefore uses:

```bash
./gradlew test --no-daemon
```

Gradle may still print:

```text
A single-use Daemon process will be forked.
Daemon will be stopped at the end of the build.
```

This is normal. Gradle creates a one-time JVM process to honor JVM settings,
then stops it immediately after the build. It is different from a persistent
background daemon.

## 16. Test Runner Command Changes

In FastAPI, the allow-list and default command now use:

```python
ALLOWED_TEST_COMMANDS = {
    "./gradlew test --no-daemon",
    "./mvnw test",
    "npm test",
    "pytest",
}


def default_test_command(repository: str | None) -> str:
    if repository == "local-workspace":
        return "./gradlew test --no-daemon"

    return "test command not configured"
```

The allow-list prevents an external request from making the test runner execute
an arbitrary shell command.

Spring Boot sends the same command:

```java
RunTestsRequest request = new RunTestsRequest(
        patchSuggestion.getIncidentId(),
        patchSuggestion.getId(),
        "local-workspace",
        "./gradlew test --no-daemon",
        patchSuggestion.getSuggestedDiff()
);
```

Both services must use the same command string because FastAPI rejects commands
that are not in its allow-list.

`test_command.split()` converts:

```text
./gradlew test --no-daemon
```

into:

```python
["./gradlew", "test", "--no-daemon"]
```

This runs the executable directly without `shell=True`, reducing command
injection risk.

## 17. Why the Successful Test Still Took 112 Seconds

The successful output included:

```text
Downloading gradle-9.3.1-bin.zip
```

The container did not yet contain that Gradle distribution. The first run had
to:

```text
download Gradle
-> extract Gradle
-> resolve dependencies
-> compile Java
-> start Spring
-> initialize H2
-> run tests
```

The result took approximately 112 seconds, close to the 120-second timeout.
Later executions should be faster while the container cache exists.

A future optimization should mount `/root/.gradle` as a Docker volume or
prewarm dependencies in a controlled build image. Increasing the timeout alone
would hide the underlying reproducibility and caching problem.

## 18. Final Verification

Run tests for the patch:

```bash
curl -X POST \
  http://localhost:8081/patch-suggestions/20/run-tests \
  | jq
```

Successful result:

```json
{
  "incidentId": 15,
  "patchSuggestionId": 20,
  "status": "PASSED",
  "passed": true,
  "testCommand": "./gradlew test --no-daemon",
  "durationMs": 112226,
  "id": 15
}
```

Run preflight again:

```bash
curl \
  http://localhost:8081/patch-suggestions/20/pull-request/preflight \
  | jq
```

Final result:

```json
{
  "patchSuggestionId": 20,
  "incidentId": 15,
  "status": "READY_FOR_PR",
  "ready": true,
  "repository": "Jake6654/sketch-my-day",
  "baseBranch": "main",
  "proposedBranch": "ai-fix/inc-15-patch-20",
  "patchValid": true,
  "testsPassed": true,
  "latestTestRunId": 15,
  "latestTestStatus": "PASSED",
  "requiresHumanReview": true,
  "blockers": []
}
```

## 19. Before and After

Before:

```text
AI patch generated
-> patch validation passed
-> isolated workspace created
-> @SpringBootTest loaded production-style DB settings
-> application initialization waited
-> test timed out
-> test result failed
-> PR preflight blocked the PR
```

After:

```text
AI patch generated
-> patch validation passed
-> isolated workspace created
-> patch applied
-> ./gradlew test --no-daemon
-> test profile activated
-> H2 database initialized
-> Spring context loaded
-> tests passed
-> single-use Gradle process stopped
-> PR preflight returned READY_FOR_PR
```

## 20. Architectural Lessons

Phase 5A separates planning from mutation. A caller can repeatedly inspect a PR
plan without creating branches or pull requests.

Phase 4 remains the source of truth for patch and test safety. Phase 5A adds
GitHub configuration validation and presentation details.

The timeout demonstrated that application code validation is only reliable when
the test environment is reproducible. Test profiles, disposable databases,
command allow-lists, timeouts, and process cleanup are part of the product's
safety design, not merely development conveniences.

H2 makes basic tests fast and independent. Testcontainers should later cover
PostgreSQL-specific integration behavior.

The completed boundary is:

```text
Phase 4  -> validate patch and execute isolated tests
Phase 5A -> build a read-only PR plan
Phase 5B -> authenticate with GitHub and create a safe branch
Phase 5C -> commit and push the validated patch
Phase 5D -> open and persist the pull request
```
