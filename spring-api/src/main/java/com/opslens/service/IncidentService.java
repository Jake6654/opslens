package com.opslens.service;

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

    public IncidentService(LogRepository logRepository, IncidentRepository incidentRepository, IncidentReportRepository incidentReportRepository) {
        this.logRepository = logRepository;
        this.incidentRepository = incidentRepository;
        this.incidentReportRepository = incidentReportRepository;
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

        IncidentReport report = new IncidentReport(
                savedIncident.getId(),
                "Initial incident created from log.",
                "AI analysis has not been run yet.",
                "Run incident analysis to identify the likely root cause.",
                0.0,
                "{}"
        );

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
