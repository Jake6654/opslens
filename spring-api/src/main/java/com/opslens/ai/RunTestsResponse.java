package com.opslens.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RunTestsResponse {

    @JsonProperty("incident_id")
    private Long incidentId;

    @JsonProperty("patch_suggestion_id")
    private Long patchSuggestionId;

    private String status;
    private Boolean passed;

    @JsonProperty("test_command")
    private String testCommand;

    private String output;

    @JsonProperty("duration_ms")
    private Integer durationMs;

    public Long getIncidentId() {
        return incidentId;
    }

    public RunTestsResponse() {
    }

    public Long getPatchSuggestionId() {
        return patchSuggestionId;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getPassed() {
        return passed;
    }

    public String getTestCommand() {
        return testCommand;
    }



    public String getOutput() {
        return output;
    }

    public Integer getDurationMs() {
        return durationMs;
    }


}
