package com.opslens.model;


import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class TestRunResult {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long incidentId;
    private Long patchSuggestionId;

    private String status;
    private Boolean passed;
    private String testCommand;

    @Column(columnDefinition = "TEXT")
    private String output;

    // why don't we use int instead of Integer?
    // Since int is primitive type, so it always must have a value
    // While Integer can be Null -> if there is no test, it would be Null
    private Integer durationMs;
    private LocalDateTime createdAt;

    public TestRunResult() {
    }

    public TestRunResult(Long incidentId, Long patchSuggestionId, String status, Boolean passed, String testCommand, String output ,Integer durationMs) {
        this.incidentId = incidentId;
        this.patchSuggestionId = patchSuggestionId;
        this.status = status;
        this.passed = passed;
        this.testCommand = testCommand;
        this.output = output;
        this.durationMs = durationMs;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
