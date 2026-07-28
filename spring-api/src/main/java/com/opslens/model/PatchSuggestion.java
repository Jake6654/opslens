package com.opslens.model;


import jakarta.persistence.*;
import org.springframework.cglib.core.Local;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Store AI-generated patch suggestions in PostgreSQL
@Entity
public class PatchSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long incidentId;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    @Column(length = 4000)
    private String patchSummary;

    @Column(columnDefinition = "TEXT")
    private String suggestedDiff;

    private String riskLevel;

    private Boolean requiresHumanReview;

    private Boolean patchValid;

    @Column(columnDefinition = "TEXT")
    private String patchValidationOutput;

    private LocalDateTime createdAt;

    public PatchSuggestion() {
    }

    public PatchSuggestion(
            Long incidentId,
            String rootCause,
            String patchSummary,
            String suggestedDiff,
            String riskLevel,
            Boolean requiresHumanReview,
            Boolean patchValid,
            String patchValidationOutput
    ) {
        this.incidentId = incidentId;
        this.rootCause = rootCause;
        this.patchSummary = patchSummary;
        this.suggestedDiff = suggestedDiff;
        this.riskLevel = riskLevel;
        this.requiresHumanReview = requiresHumanReview;
        this.patchValid = patchValid;
        this.patchValidationOutput = patchValidationOutput;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getRootCause() {
        return rootCause;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getPatchSummary() {
        return patchSummary;
    }

    public String getSuggestedDiff() {
        return suggestedDiff;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public Boolean getRequiresHumanReview() {
        return requiresHumanReview;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Boolean getPatchValid() {
        return patchValid;
    }

    public String getPatchValidationOutput() {
        return patchValidationOutput;
    }
}
