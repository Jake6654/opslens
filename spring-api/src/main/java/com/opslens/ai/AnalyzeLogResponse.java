package com.opslens.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

// Spring Boot needs a Java Object to receive the JSON returned by FastAPI
public class AnalyzeLogResponse {


    private String summary;

    //FastAPI response currently may return snake_case fields
    // But Java fields use camelCase, so we need to handle this
    @JsonProperty("suspected_root_cause")
    private String suspectedRootCause;

    @JsonProperty("recommended_action")
    private String recommendedAction;

    private Double confidence;

    @JsonProperty("raw_response")
    private String rawResponse;

    public AnalyzeLogResponse() {
    }


    public String getSummary() {
        return summary;
    }

    public String getSuspectedRootCause() {
        return suspectedRootCause;
    }


    public Double getConfidence() {
        return confidence;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }


    public String getRawResponse() {
        return rawResponse;
    }
}
