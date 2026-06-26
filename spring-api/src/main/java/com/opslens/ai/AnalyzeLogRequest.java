package com.opslens.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

// Spring Boot needs a java object that matches the JSON FastAPI expects.
public class AnalyzeLogRequest {

    @JsonProperty("incident_id")
    private Long incidentId;

    @JsonProperty("log_id")
    private Long logId;


    private String project;
    private String environment;
    private String service;
    private String severity;
    private String message;

    public AnalyzeLogRequest() {
    }

    public AnalyzeLogRequest(
            Long incidentId,
            Long logId,
            String project,
            String environment,
            String service,
            String severity,
            String message
    ) {
        this.incidentId = incidentId;
        this.logId = logId;
        this.project = project;
        this.environment = environment;
        this.service = service;
        this.severity = severity;
        this.message = message;
    }

    @JsonProperty("incident_id")
    public Long getIncidentId() {
        return incidentId;
    }

    @JsonProperty("log_id")
    public Long getLogId() {
        return logId;
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
}
