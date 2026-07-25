package com.opslens.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AnalyzeTestFailureResponse {

    @JsonProperty("incident_id")
    private Long incidentId;

    @JsonProperty("patch_suggestion_id")
    private Long patchSuggestionId;

    @JsonProperty("test_run_id")
    private Long testRunId;

    @JsonProperty("failure_summary")
    private String failureSummary;

    @JsonProperty("likely_cause")
    private String likelyCause;

    @JsonProperty("recommended_action")
    private String recommendedAction;

    private Double confidence;

    @JsonProperty("raw_response")
    private String rawResponse;

    public AnalyzeTestFailureResponse() {
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public Long getPatchSuggestionId() {
        return patchSuggestionId;
    }

    public Long getTestRunId() {
        return testRunId;
    }

    public String getFailureSummary() {
        return failureSummary;
    }

    public String getLikelyCause() {
        return likelyCause;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}