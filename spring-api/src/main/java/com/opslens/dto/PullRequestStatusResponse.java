package com.opslens.dto;

import java.time.LocalDateTime;

public class PullRequestStatusResponse {

    private final Long patchSuggestionId;
    private final Long incidentId;
    private final String repository;
    private final String baseBranch;
    private final String headBranch;
    private final String commitSha;
    private final Long pullRequestNumber;
    private final String pullRequestUrl;
    private final String status;
    private final boolean refreshedFromGitHub;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public PullRequestStatusResponse(Long patchSuggestionId, Long incidentId, String repository, String baseBranch, String headBranch, String commitSha, Long pullRequestNumber, String pullRequestUrl, String status, boolean refreshedFromGitHub, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.patchSuggestionId = patchSuggestionId;
        this.incidentId = incidentId;
        this.repository = repository;
        this.baseBranch = baseBranch;
        this.headBranch = headBranch;
        this.commitSha = commitSha;
        this.pullRequestNumber = pullRequestNumber;
        this.pullRequestUrl = pullRequestUrl;
        this.status = status;
        this.refreshedFromGitHub = refreshedFromGitHub;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getHeadBranch() {
        return headBranch;
    }

    public Long getPullRequestNumber() {
        return pullRequestNumber;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getStatus() {
        return status;
    }

    public String getPullRequestUrl() {
        return pullRequestUrl;
    }

    // true when the value is updated by GitHub API from this request
    // false it comes from the PostgreSQL
    public boolean isRefreshedFromGitHub() {
        return refreshedFromGitHub;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
