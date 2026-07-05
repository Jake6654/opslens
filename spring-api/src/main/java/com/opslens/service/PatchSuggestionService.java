package com.opslens.service;

import com.opslens.ai.AiOrchestratorClient;
import com.opslens.ai.PatchCodeResult;
import com.opslens.ai.PatchSuggestionRequest;
import com.opslens.ai.PatchSuggestionResponse;
import com.opslens.model.CodeSearchResult;
import com.opslens.model.Incident;
import com.opslens.model.IncidentReport;
import com.opslens.model.PatchSuggestion;
import com.opslens.repository.CodeSearchResultRepository;
import com.opslens.repository.IncidentReportRepository;
import com.opslens.repository.IncidentRepository;
import com.opslens.repository.PatchSuggestionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PatchSuggestionService {

    private final IncidentRepository incidentRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final CodeSearchResultRepository codeSearchResultRepository;
    private final PatchSuggestionRepository patchSuggestionRepository;
    private final AiOrchestratorClient aiOrchestratorClient;

    public PatchSuggestionService(
            IncidentRepository incidentRepository,
            IncidentReportRepository incidentReportRepository,
            CodeSearchResultRepository codeSearchResultRepository,
            PatchSuggestionRepository patchSuggestionRepository,
            AiOrchestratorClient aiOrchestratorClient
    ) {
        this.incidentRepository = incidentRepository;
        this.incidentReportRepository = incidentReportRepository;
        this.codeSearchResultRepository = codeSearchResultRepository;
        this.patchSuggestionRepository = patchSuggestionRepository;
        this.aiOrchestratorClient = aiOrchestratorClient;
    }

    public PatchSuggestion suggestPatchForIncident(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        IncidentReport report = incidentReportRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new IllegalStateException("Incident report not found: " + incidentId));

        List<CodeSearchResult> codeResults =
                codeSearchResultRepository.findByIncidentId(incidentId);

        if (codeResults.isEmpty()) {
            throw new IllegalStateException("Run code search before patch suggestion");
        }

        List<PatchCodeResult> patchCodeResults = codeResults.stream()
                .map(this::toPatchCodeResult)
                .toList();

        PatchSuggestionRequest request = new PatchSuggestionRequest(
                incident.getId(),
                report.getSummary(),
                report.getSuspectedRootCause(),
                report.getRecommendedAction(),
                patchCodeResults
        );

        PatchSuggestionResponse response = aiOrchestratorClient.suggestPatch(request);

        PatchSuggestion suggestion = new PatchSuggestion(
                incident.getId(),
                response.getRootCause(),
                response.getPatchSummary(),
                response.getSuggestedDiff(),
                response.getRiskLevel(),
                response.getRequiresHumanReview()
        );

        return patchSuggestionRepository.save(suggestion);
    }

    public List<PatchSuggestion> getPatchSuggestions(Long incidentId) {
        return patchSuggestionRepository.findByIncidentId(incidentId);
    }

    private PatchCodeResult toPatchCodeResult(CodeSearchResult result) {
        return new PatchCodeResult(
                result.getRepository(),
                result.getFilePath(),
                result.getSnippet(),
                result.getRelevanceReason(),
                result.getScore()
        );
    }
}
