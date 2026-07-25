package com.opslens.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AnalyzeTestFailureRequest {

    @JsonProperty("incident_id")
    private Long incidentId;

    @JsonProperty("patch_suggestion_id")
    private Long patchSuggestionId;

    @JsonProperty("test_run_id")
    private Long testRunId;

    @JsonProperty("test_command")
    private String testCommand;

    private String status;
    private String output;

    public AnalyzeTestFailureRequest(
            Long incidentId,
            Long patchSuggestionId,
            Long testRunId,
            String testCommand,
            String status,
            String output
    ) {
        this.incidentId = incidentId;
        this.patchSuggestionId = patchSuggestionId;
        this.testRunId = testRunId;
        this.testCommand = testCommand;
        this.status = status;
        this.output = output;
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

    public String getTestCommand() {
        return testCommand;
    }

    public String getStatus() {
        return status;
    }

    public String getOutput() {
        return output;
    }
}