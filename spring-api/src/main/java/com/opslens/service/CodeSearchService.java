package com.opslens.service;

import com.opslens.ai.AiOrchestratorClient;
import com.opslens.ai.CodeSearchItem;
import com.opslens.ai.CodeSearchRequest;
import com.opslens.ai.CodeSearchResponse;
import com.opslens.model.CodeSearchResult;
import com.opslens.model.Incident;
import com.opslens.model.IncidentReport;
import com.opslens.repository.CodeSearchResultRepository;
import com.opslens.repository.IncidentReportRepository;
import com.opslens.repository.IncidentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CodeSearchService {

    private final IncidentRepository incidentRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final CodeSearchResultRepository codeSearchResultRepository;
    private final AiOrchestratorClient aiOrchestratorClient;

    public CodeSearchService(IncidentRepository incidentRepository, IncidentReportRepository incidentReportRepository, CodeSearchResultRepository codeSearchResultRepository, AiOrchestratorClient aiOrchestratorClient) {
        this.incidentRepository = incidentRepository;
        this.incidentReportRepository = incidentReportRepository;
        this.codeSearchResultRepository = codeSearchResultRepository;
        this.aiOrchestratorClient = aiOrchestratorClient;
    }

    public List<CodeSearchResult> searchCodeForIncident(Long incidentId) {
        // First, it loads the incident
        // if the incident exists, return it, otherwise throw an error
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        // Second, it loads the incidentReport
        // Here we allow null because code search can still run even if a report does not exist yet
        IncidentReport report = incidentReportRepository.findByIncidentId(incidentId)
                .orElse(null);

        // Third, Create a request DTO for FastAPI
        CodeSearchRequest request = new CodeSearchRequest(
                incident.getId(),
                incident.getProject(),
                incident.getEnvironment(),
                incident.getService(),
                incident.getSeverity(),
                incident.getTitle(),
                report != null ? report.getSummary() : null,
                report != null ? report.getSuspectedRootCause() : null
        );

        // Fourth, it calls FastAPI
        // The service does not know HTTP details, it simply asks the client object to perform code search
        CodeSearchResponse response = aiOrchestratorClient.searchCode(request);


        // Fifth, it converts the response items into database entities
        List<CodeSearchResult> results = response.getResults().stream() // process each item in the list
                .map(item -> toEntity(incidentId, item)) // transform each CoderSearchItem into a CodeSearchResult
                .toList();

        return codeSearchResultRepository.saveAll(results);
    }

    public List<CodeSearchResult> getCodeSearchResults(Long incidentId){
        return codeSearchResultRepository.findByIncidentId(incidentId);
    }


    // Helper Method, it helps convert a DTO into a database entity
    private CodeSearchResult toEntity(Long incidentId, CodeSearchItem item){
        return new CodeSearchResult(
                incidentId,
                item.getRepository(),
                item.getFilePath(),
                item.getSymbolName(),
                item.getSnippet(),
                item.getRelevanceReason(),
                item.getScore()
        );
    }
}
