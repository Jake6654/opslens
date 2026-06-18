package com.opslens.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sourceLogId;

    private String project;
    private String environment;
    private String service;


    private String severity;
    private String status;
    private String title;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Incident() {

    }

    public Incident(
            Long sourceLogId,
            String project,
            String environment,
            String service,
            String severity,
            String status,
            String title
    ) {
        this.sourceLogId = sourceLogId;
        this.project = project;
        this.environment = environment;
        this.service = service;
        this.severity = severity;
        this.status = status;
        this.title = title;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSourceLogId() {
        return sourceLogId;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getProject() {
        return project;
    }

    public String getService() {
        return service;
    }

    public String getSeverity() {
        return severity;
    }

    public String getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
