package com.opslens.service;

import com.opslens.ai.AiOrchestratorClient;
import com.opslens.ai.AnalyzeTestFailureRequest;
import com.opslens.ai.AnalyzeTestFailureResponse;
import com.opslens.model.TestFailureAnalysis;
import com.opslens.model.TestRunResult;
import com.opslens.repository.TestFailureAnalysisRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TestFailureAnalysisService {

    private final AiOrchestratorClient aiOrchestratorClient;
    private final TestFailureAnalysisRepository repository;

    public TestFailureAnalysisService(
            AiOrchestratorClient aiOrchestratorClient,
            TestFailureAnalysisRepository repository
    ) {
        this.aiOrchestratorClient = aiOrchestratorClient;
        this.repository = repository;
    }

    public Optional<TestFailureAnalysis> getAnalysisByTestRunId(Long testRunId) {
        return repository.findByTestRunId(testRunId);
    }

    public TestFailureAnalysis analyzeAndSave(TestRunResult testRunResult) {
        Optional<TestFailureAnalysis> existing =
                repository.findByTestRunId(testRunResult.getId());

        if (existing.isPresent()) {
            return existing.get();
        }

        AnalyzeTestFailureRequest request = new AnalyzeTestFailureRequest(
                testRunResult.getIncidentId(),
                testRunResult.getPatchSuggestionId(),
                testRunResult.getId(),
                testRunResult.getTestCommand(),
                testRunResult.getStatus(),
                testRunResult.getOutput()
        );

        AnalyzeTestFailureResponse response =
                aiOrchestratorClient.analyzeTestFailure(request);

        TestFailureAnalysis analysis = new TestFailureAnalysis(
                response.getIncidentId(),
                response.getPatchSuggestionId(),
                response.getTestRunId(),
                response.getFailureSummary(),
                response.getLikelyCause(),
                response.getRecommendedAction(),
                response.getConfidence(),
                response.getRawResponse()
        );

        return repository.save(analysis);
    }
}