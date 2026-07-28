package com.opslens.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RunTestsRequest {

    @JsonProperty("incident_id")
    private Long incidentId;

    @JsonProperty("patch_suggestion_id")
    private Long patchSuggestionId;

    private String repository;

    @JsonProperty("test_command")
    private String testCommand;

    @JsonProperty("suggested_diff")
    private String suggestedDiff;

    public RunTestsRequest(Long incidentId, Long patchSuggestionId, String repository, String testCommand, String suggestedDiff) {
        this.incidentId = incidentId;
        this.patchSuggestionId = patchSuggestionId;
        this.repository = repository;
        this.testCommand = testCommand;
        this.suggestedDiff = suggestedDiff;
    }

    public String getSuggestedDiff() {
        return suggestedDiff;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public Long getPatchSuggestionId() {
        return patchSuggestionId;
    }

    public String getTestCommand() {
        return testCommand;
    }

    public String getRepository() {
        return repository;
    }
}
