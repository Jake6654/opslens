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

    public String getCommitSha() {
        return commitSha;
    }

    public String getPreviousCommitSha() {
        return previousCommitSha;
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
