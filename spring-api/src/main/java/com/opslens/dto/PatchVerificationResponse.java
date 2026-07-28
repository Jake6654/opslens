package com.opslens.dto;

import java.util.List;

public class PatchVerificationResponse {

    private final Long patchSuggestionId;
    private final Long incidentId;
    private final String status;
    private final boolean readyForPullRequest;
    private final Boolean patchValid;
    private final Boolean testsPassed;
    private final Long latestTestRunId;
    private final String latestTestStatus;
    private final boolean requiresHumanReview;
    private final List<String> blockers;

    public PatchVerificationResponse(
            Long patchSuggestionId,
            Long incidentId,
            String status,
            boolean readyForPullRequest,
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
        this.readyForPullRequest = readyForPullRequest;
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

    public boolean isReadyForPullRequest() {
        return readyForPullRequest;
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
