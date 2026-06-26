package com.opslens.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CodeSearchRequest {

    @JsonProperty("incident_id")
    private Long incidentId;

    private String project;
    private String environment;
    private String service;
    private String severity;
    private String message;

    @JsonProperty("analysis_summary")
    private String analysisSummary;

    @JsonProperty("suspected_root_cause")
    private String suspectedRootCause;

    public CodeSearchRequest() {
    }

    public CodeSearchRequest(
            Long incidentId,
            String project,
            String environment,
            String service,
            String severity,
            String message,
            String analysisSummary,
            String suspectedRootCause
    ) {
        this.incidentId = incidentId;
        this.project = project;
        this.environment = environment;
        this.service = service;
        this.severity = severity;
        this.message = message;
        this.analysisSummary = analysisSummary;
        this.suspectedRootCause = suspectedRootCause;
    }

    @JsonProperty("incident_id")
    public Long getIncidentId() {
        return incidentId;
    }

    public String getProject() {
        return project;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getService() {
        return service;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    @JsonProperty("analysis_summary")
    public String getAnalysisSummary() {
        return analysisSummary;
    }

    @JsonProperty("suspected_root_cause")
    public String getSuspectedRootCause() {
        return suspectedRootCause;
    }
}
