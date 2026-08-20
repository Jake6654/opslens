package com.opslens.dto;

import java.time.LocalDateTime;

public class CreatePullRequestResponse {

    private final Long patchSuggestionId;
    private final Long incidentId;
    private final String repository;
    private final String baseBranch;
    private final String headBranch;
    private final String commitSha;
    private final Long pullRequestNumber;
    private final String pullRequestUrl;
    private final String pullRequestState;
    private final String operationStatus;
    private final boolean created;
    private final LocalDateTime createdAt;

    public CreatePullRequestResponse(Long patchSuggestionsId, Long incidentId, String repository, String baseBranch, String headBranch, String commitSha, Long pullRequestNumber, String pullRequestUrl, String pullRequestState, String operationStatus, boolean created, LocalDateTime createdAt) {
        this.patchSuggestionId = patchSuggestionsId;
        this.incidentId = incidentId;
        this.repository = repository;
        this.baseBranch = baseBranch;
        this.headBranch = headBranch;
        this.commitSha = commitSha;
        this.pullRequestNumber = pullRequestNumber;
        this.pullRequestUrl = pullRequestUrl;
        this.pullRequestState = pullRequestState;
        this.operationStatus = operationStatus;
        this.created = created;
        this.createdAt = createdAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isCreated() {
        return created;
    }

    // This field is about the API request result (Created or Already_exists)
    public String getOperationStatus() {
        return operationStatus;
    }

    public Long getPatchSuggestionsId() {
        return patchSuggestionId;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getHeadBranch() {
        return headBranch;
    }

    public String getBaseBranch() {
        return baseBranch;
    }

    public String getRepository() {
        return repository;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getPullRequestUrl() {
        return pullRequestUrl;
    }

    public Long getPullRequestNumber() {
        return pullRequestNumber;
    }

    // OPEN, CLOSED, MERGED
    public String getPullRequestState() {
        return pullRequestState;
    }
}
