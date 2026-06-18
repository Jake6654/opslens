package com.opslens.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class IncidentReport {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long incidentId;

    @Column(length = 4000)
    private String summary;

    @Column(length = 4000)
    private String suspectedRootCause;

    @Column(length = 4000)
    private String recommendedAction;

    private Double confidence;

    @Column(length = 8000)
    private String rawResponse;

    private LocalDateTime createdAt;

    public IncidentReport() {
    }

    public IncidentReport(
            Long incidentId,
            String summary,
            String suspectedRootCause,
            String recommendedAction,
            Double confidence,
            String rawResponse
    ) {
        this.incidentId = incidentId;
        this.summary = summary;
        this.suspectedRootCause = suspectedRootCause;
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

    public String getSummary() {
        return summary;
    }

    public String getSuspectedRootCause() {
        return suspectedRootCause;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
