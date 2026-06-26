package com.opslens.service;

import com.opslens.ai.AiOrchestratorClient;
import com.opslens.ai.AnalyzeLogRequest;
import com.opslens.ai.AnalyzeLogResponse;
import com.opslens.model.Incident;
import com.opslens.model.IncidentReport;
import com.opslens.model.LogItem;
import com.opslens.repository.IncidentReportRepository;
import com.opslens.repository.IncidentRepository;
import com.opslens.repository.LogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class IncidentService {

    // Why private final? the dependencies are required and should not be reassigned
    private final LogRepository logRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final AiOrchestratorClient aiOrchestratorClient;

    public IncidentService(LogRepository logRepository, IncidentRepository incidentRepository, IncidentReportRepository incidentReportRepository, AiOrchestratorClient aiOrchestratorClient) {
        this.logRepository = logRepository;
        this.incidentRepository = incidentRepository;
        this.incidentReportRepository = incidentReportRepository;
        this.aiOrchestratorClient = aiOrchestratorClient;
    }

    public Incident createIncidentFromLog(Long logId){
        LogItem log = logRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("Log not found: " + logId));

        Incident incident = new Incident(
                log.getId(),
                log.getProject(),
                log.getEnvironment(),
                log.getService(),
                log.getLevel(),
                "OPEN",
                buildIncidentTitle(log)
        );

        Incident savedIncident = incidentRepository.save(incident);
        IncidentReport report;

        // now incident comes from FastAPI instead of hardcoded Java placeholder text
        try {
            AnalyzeLogRequest analyzeRequest = new AnalyzeLogRequest(
                    savedIncident.getId(),
                    log.getId(),
                    log.getProject(),
                    log.getEnvironment(),
                    log.getService(),
                    log.getLevel(),
                    log.getMessage()
            );

            AnalyzeLogResponse analyzeResponse = aiOrchestratorClient.analyzeLog(analyzeRequest);

                report = new IncidentReport(
                    savedIncident.getId(),
                    analyzeResponse.getSummary(),
                    analyzeResponse.getSuspectedRootCause(),
                    analyzeResponse.getRecommendedAction(),
                    analyzeResponse.getConfidence(),
                    analyzeResponse.getRawResponse()
            );

        } catch (Exception e) {
            System.out.println("AI orchestrator call failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            report = new IncidentReport(
                    savedIncident.getId(),
                    "Incident created, but AI orchestrator call failed.",
                    "AI orchestrator was unavailable or returned an invalid response.",
                    "Check ai-orchestrator service health and retry analysis later.",
                    0.0,
                    "{\"error\":\"ai_orchestrator_unavailable\"}"
            );
        }

        incidentReportRepository.save(report);
        return savedIncident;

    }

    public List<Incident> getIncidents() {
        return incidentRepository.findAll();
    }

    public Optional<Incident> getIncidentById(Long id) {
        return incidentRepository.findById(id);
    }

    public Optional<IncidentReport> getReportByIncidentId(Long incidentId) {
        return incidentReportRepository.findByIncidentId(incidentId);
    }

    private String buildIncidentTitle(LogItem log) {
        String service = log.getService() == null ? "unknown-service" : log.getService();
        String message = log.getMessage() == null ? "No message" : log.getMessage();

        if (message.length() > 80) {
            message = message.substring(0,80);
        }

        return service + ":" + message;



    }
}
