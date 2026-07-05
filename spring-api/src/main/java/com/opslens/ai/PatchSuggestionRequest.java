package com.opslens.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
// Represents the full request Spring Boot sends to FastAPI /suggest-patch
// This class packages incident analysis + related code snippets into one request
// FastAPI expects
// {
//  "incident_id": 12,
//  "summary": "...",
//  "suspected_root_cause": "...",
//  "recommended_action": "...",
//  "code_results": [
//    {
//      "repository": "local-workspace",
//      "file_path": "...",
//      "snippet": "...",
//      "relevance_reason": "...",
//      "score": 0.75
//    }
//  ]
//}
public class PatchSuggestionRequest {

    @JsonProperty("incident_id")
    private Long incidentId;

    private String summary;

    @JsonProperty("suspected_root_cause")
    private String suspectedRootCause;

    @JsonProperty("recommended_action")
    private String recommendedAction;

    @JsonProperty("code_results")
    private List<PatchCodeResult> codeResults;

    public PatchSuggestionRequest() {
    }

    public PatchSuggestionRequest(
            Long incidentId,
            String summary,
            String suspectedRootCause,
            String recommendedAction,
            List<PatchCodeResult> codeResults
    ) {
        this.incidentId = incidentId;
        this.summary = summary;
        this.suspectedRootCause = suspectedRootCause;
        this.recommendedAction = recommendedAction;
        this.codeResults = codeResults;
    }

    @JsonProperty("incident_id")
    public Long getIncidentId() {
        return incidentId;
    }

    public String getSummary() {
        return summary;
    }

    @JsonProperty("suspected_root_cause")
    public String getSuspectedRootCause() {
        return suspectedRootCause;
    }

    @JsonProperty("recommended_action")
    public String getRecommendedAction() {
        return recommendedAction;
    }

    @JsonProperty("code_results")
    // One incident can have multiple related files that's why it returns List<PatchCodeResult>
    public List<PatchCodeResult> getCodeResults() {
        return codeResults;
    }
}