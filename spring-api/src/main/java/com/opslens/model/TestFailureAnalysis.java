package com.opslens.model;

import jakarta.persistence.*;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;


@Entity
public class TestFailureAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long incidentId;
    private Long patchSuggestionId;
    private Long testRunId;

    @Column(columnDefinition = "TEXT")
    private String failureSummary;

    @Column(columnDefinition = "TEXT")
    private String likelyCause;

    @Column(columnDefinition = "TEXT")
    private String recommendedAction;

    private Double confidence;

    @Column(columnDefinition = "TEXT")
    private String rawResponse;

    private LocalDateTime createdAt;

    public TestFailureAnalysis() {
    }

    public TestFailureAnalysis(Long incidentId, Long patchSuggestionId, Long testRunId, String failureSummary, String likelyCause, String recommendedAction, Double confidence, String rawResponse) {
        this.incidentId = incidentId;
        this.patchSuggestionId = patchSuggestionId;
        this.testRunId = testRunId;
        this.failureSummary = failureSummary;
        this.likelyCause = likelyCause;
        this.recommendedAction = recommendedAction;
        this.confidence = confidence;
        this.rawResponse = rawResponse;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
