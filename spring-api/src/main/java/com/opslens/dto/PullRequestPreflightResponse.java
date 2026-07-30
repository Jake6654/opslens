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