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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * Looks up a branch in the configured GitHub repository.
     * A missing branch is represented by Optional.empty() instead of an error.
     */
    public Optional<GitHubBranchReference> findBranch(String branch) {
        // Fail locally before sending a malformed or unauthenticated request.
        validateConfiguration();
        validateBranch(branch);

        String ref = "heads/" + branch;
        // Build a URL like GET /repos/{owner}/{repository}/git/ref/heads/{branch}.
        URI uri = URI.create(repositoryApiUrl()
                + "/git/ref/"
                + encodePathValue(ref));

        HttpRequest request = requestBuilder(uri)
                .GET()
                .build();

        HttpResponse<String> response = send(request);

        // A missing branch is an expected lookup result during branch creation.
        if (response.statusCode() == 404) {
            return Optional.empty();
        }

        requireStatus(response, 200, "read GitHub branch");
        return Optional.of(parseReference(response.body()));
    }

    /**
     * Creates a new Git branch that points to the supplied base commit SHA.
     */
    public GitHubBranchReference createBranch(
            String branch,
            String sha
    ) {
        // Validate all inputs before performing a GitHub mutation.
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

        // GitHub creates branches through the Git References API.
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

    /**
     * Returns the configured repository in owner/repository format.
     */
    public String qualifiedRepository() {
        return properties.getOwner().trim()
                + "/"
                + properties.getRepository().trim();
    }

    /**
     * Creates a request builder with headers required by every GitHub API call.
     */
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

    /**
     * Sends an HTTP request and converts transport failures into domain errors.
     */
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
            // Preserve the interrupted state so higher-level code can observe it.
            Thread.currentThread().interrupt();
            throw new GitHubApiException(
                    "GitHub request was interrupted.",
                    error
            );
        }
    }

    /**
     * Converts a GitHub reference JSON response into an immutable Java record.
     */
    private GitHubBranchReference parseReference(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String ref = root.path("ref").asText();
            String sha = root.path("object").path("sha").asText();
            String url = root.path("url").asText();

            // A successful response without a ref or SHA cannot be trusted.
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

    /**
     * Verifies that GitHub returned the status expected for an API operation.
     */
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

    /**
     * Builds the base API URL for the configured GitHub repository.
     */
    private String repositoryApiUrl() {
        return removeTrailingSlash(properties.getApiBaseUrl())
                + "/repos/"
                + encodePathValue(properties.getOwner().trim())
                + "/"
                + encodePathValue(properties.getRepository().trim());
    }

    /**
     * Percent-encodes a dynamic value before placing it in a URL path.
     */
    private String encodePathValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    /**
     * Removes trailing slashes so URL segments can be joined consistently.
     */
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

    /**
     * Serializes a branch creation payload into JSON for GitHub.
     */
    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(
                    "Could not serialize the GitHub branch request.",
                    error
            );
        }
    }

    /**
     * Ensures all required GitHub connection settings are available.
     */
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

    /**
     * Rejects missing or obviously unsafe Git branch names.
     */
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

    /**
     * Produces a bounded, single-line GitHub error body for safe diagnostics.
     */
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

    /**
     *  Reads a Git commit object so the workflow can obtain its tree and parents
     */
    public GitHubCommitObject getCommit(String commitSha){
        validateConfiguration();

        if (commitSha == null || commitSha.isBlank()){
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

    public Optional<GitHubPullRequest> findOpenPullRequest(
            String headBranch,
            String baseBranch
    ) {
        validateConfiguration();
        validateBranch(headBranch);
        validateBranch(baseBranch);


        // GitHub head filter like Jake6654:ai-fix/inc-15-patch-20
        String qualifiedHead = properties.getOwner().trim()
                + ":"
                + headBranch;

        // 조회 url 만들기
        /**
         * GET /repos/Jake6654/sketch-my-day/pulls
         *     ?state=open
         *     &head=Jake6654:ai-fix/inc-15-patch-20
         *     &base=main
         *     &per_page=1 (하나만 필요)
         */
        URI uri = URI.create(
                repositoryApiUrl()
                        + "/pulls?state=open"
                        + "&head=" + encodeQueryValue(qualifiedHead)
                        + "&base=" + encodeQueryValue(baseBranch)
                        + "&per_page=1"
        );

        HttpRequest request = requestBuilder(uri)
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        // if it returns the different state, returns GitHubApiException
        requireStatus(response, 200, "find existing GitHub pull request");

        try {
            JsonNode root = objectMapper.readTree(response.body());

            if (!root.isArray()) {
                throw new GitHubApiException(
                        502,
                        "GitHub returned an invalid pull request list."
                );
            }

            if (root.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(parsePullRequest(root.get(0)));
        } catch (JsonProcessingException error) {
            throw new GitHubApiException(
                    "Could not parse the GitHub pull request list.",
                    error
            );
        }
    }

    public GitHubPullRequest createPullRequest(
            String title,
            String body,
            String headBranch,
            String baseBranch,
            boolean draft
    ) {
        validateConfiguration();
        validateBranch(headBranch);
        validateBranch(baseBranch);

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Pull request title is required."
            );
        }

        if (headBranch.equals(baseBranch)) {
            throw new IllegalArgumentException(
                    "Pull request head and base branches must be different."
            );
        }

        // create GitHub payload
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("body", body == null ? "" : body);
        payload.put("head", headBranch);
        payload.put("base", baseBranch);
        payload.put("draft", draft);

        HttpRequest request = requestBuilder(
                URI.create(repositoryApiUrl() + "/pulls")
        )
                .header("Content-Type", "application/json")
                // toJson returns Java Map into JSON
                .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                .build();

        HttpResponse<String> response = send(request);
        requireStatus(response, 201, "create GitHub pull request");

        try {
            // Deserialize JSON into a JsonNode -> GitHubPullRequest
            JsonNode root = objectMapper.readTree(response.body());
            return parsePullRequest(root);
        } catch (JsonProcessingException error) {
            throw new GitHubApiException(
                    "Could not parse the created GitHub pull request.",
                    error
            );
        }
    }

    /**
     * Loads one pull request from the configured GitHub repository
     */
    public GitHubPullRequest getPullRequest(Long pullRequestNumber) {

        // check whether GitHub token, owner, repo are set properly
        validateConfiguration();

        if (pullRequestNumber == null || pullRequestNumber <= 0) {
            throw new IllegalArgumentException(
                    "A valid pull request number is required."
            );
        }

        // https://api.github.com/repos/Jake6654/sketch-my-day/pulls/27
        URI uri = URI.create(
                repositoryApiUrl()
                        + "/pulls/"
                        + pullRequestNumber
        );

        HttpRequest request = requestBuilder(uri)
                .GET()
                .build();

        HttpResponse<String> response = send(request);

        requireStatus(
                response,
                200,
                "read GitHub pull request"
        );

        try {
            JsonNode root = objectMapper.readTree(response.body());
            return parsePullRequest(root);
        } catch (JsonProcessingException error) {
            throw new GitHubApiException(
                    "Could not parse the GitHub pull request response.",
                    error
            );
        }
    }

    private String encodeQueryValue(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }


    private GitHubPullRequest parsePullRequest(JsonNode root) {
        // path 을 사용하면 field 가 없어도 null 을 반환하지 않고 missing node 을 반환한다
        long number = root.path("number").asLong();
        String apiUrl = root.path("url").asText();
        String htmlUrl = root.path("html_url").asText();
        String state = root.path("state").asText();
        String title = root.path("title").asText();
        boolean merged = root.path("merged").asBoolean(false);
        boolean draft = root.path("draft").asBoolean(false);
        String headBranch = root.path("head").path("ref").asText();
        String headSha = root.path("head").path("sha").asText();
        String baseBranch = root.path("base").path("ref").asText();

        if (number <= 0
                || htmlUrl.isBlank()
                || state.isBlank()
                || headBranch.isBlank()
                || headSha.isBlank()
                || baseBranch.isBlank()) {
            throw new GitHubApiException(
                    502,
                    "GitHub returned an invalid pull request response."
            );
        }

        return new GitHubPullRequest(
                number,
                apiUrl,
                htmlUrl,
                state,
                title,
                draft,
                merged,
                headBranch,
                headSha,
                baseBranch
        );
    }




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

}
