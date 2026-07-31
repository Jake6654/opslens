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