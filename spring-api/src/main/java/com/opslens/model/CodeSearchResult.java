package com.opslens.model;


import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class CodeSearchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long incidentId;

    private String repository;

    private String filePath;

    private String symbolName;

    @Column(length = 8000)
    private String snippet;

    @Column(length = 4000)
    private String relevanceReason;

    private Double score;

    private LocalDateTime createdAt;

    // JPA needs it so it can create objects when reading rows form the database
    public CodeSearchResult() {
    }

    public CodeSearchResult(Long incidentId, String repository, String filePath, String symbolName, String snippet, String relevanceReason, Double score) {
        this.incidentId = incidentId;
        this.repository = repository;
        this.filePath = filePath;
        this.symbolName = symbolName;
        this.snippet = snippet;
        this.relevanceReason = relevanceReason;
        this.score = score;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getRelevanceReason() {
        return relevanceReason;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getRepository() {
        return repository;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getSymbolName() {
        return symbolName;
    }

    public String getSnippet() {
        return snippet;
    }

    public Double getScore() {
        return score;
    }
}
