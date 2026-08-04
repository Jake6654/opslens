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
}
