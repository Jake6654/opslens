package com.opslens.ai;

// Represents the response Spring Boot receives from FastAPI /suggest-patch
// FastAPI returns
//{
//        "incident_id": 12,
//        "root_cause": "...",
//        "patch_summary": "...",
//        "suggested_diff": "...",
//        "risk_level": "LOW",
//        "requires_human_review": true
//        }

import com.fasterxml.jackson.annotation.JsonProperty;

public class PatchSuggestionResponse {

    @JsonProperty("incident_id")
    private Long incidentId;

    @JsonProperty("root_cause")
    private String rootCause;

    @JsonProperty("patch_summary")
    private String patchSummary;

    @JsonProperty("suggested_diff")
    private String suggestedDiff;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("requires_human_review")
    private Boolean requiresHumanReview;

    public PatchSuggestionResponse() {
    }

    @JsonProperty("incident_id")
    public Long getIncidentId() {
        return incidentId;
    }

    @JsonProperty("root_cause")
    public String getRootCause() {
        return rootCause;
    }

    @JsonProperty("patch_summary")
    public String getPatchSummary() {
        return patchSummary;
    }

    @JsonProperty("suggested_diff")
    public String getSuggestedDiff() {
        return suggestedDiff;
    }

    @JsonProperty("risk_level")
    public String getRiskLevel() {
        return riskLevel;
    }

    @JsonProperty("requires_human_review")
    public Boolean getRequiresHumanReview() {
        return requiresHumanReview;
    }
}