package com.opslens.service;

import com.opslens.ai.AiOrchestratorClient;
import com.opslens.ai.RunTestsRequest;
import com.opslens.ai.RunTestsResponse;
import com.opslens.model.PatchSuggestion;
import com.opslens.model.TestFailureAnalysis;
import com.opslens.model.TestRunResult;
import com.opslens.repository.PatchSuggestionRepository;
import com.opslens.repository.TestRunResultRepository;
import org.springframework.stereotype.Service;

import java.nio.channels.IllegalChannelGroupException;
import java.util.List;

@Service
public class TestRunnerService {

    private final PatchSuggestionRepository patchSuggestionRepository;
    private final TestRunResultRepository testRunResultRepository;
    private final AiOrchestratorClient aiOrchestratorClient;
    private final TestFailureAnalysisService testFailureAnalysisService;

    public TestRunnerService(PatchSuggestionRepository patchSuggestionRepository, TestRunResultRepository testRunResultRepository, AiOrchestratorClient aiOrchestratorClient
    , TestFailureAnalysisService testFailureAnalysisService) {
        this.patchSuggestionRepository = patchSuggestionRepository;
        this.testRunResultRepository = testRunResultRepository;
        this.aiOrchestratorClient = aiOrchestratorClient;
        this.testFailureAnalysisService = testFailureAnalysisService;
    }

    public TestRunResult runTestsForPatchSuggestion(Long patchSuggestionId) {
        PatchSuggestion patchSuggestion = patchSuggestionRepository.findById(patchSuggestionId)
                .orElseThrow(() -> new IllegalArgumentException("Patch suggestion not found: " + patchSuggestionId));


        RunTestsRequest request = new RunTestsRequest(
                patchSuggestion.getIncidentId(),
                patchSuggestion.getId(),
                "local-workspace",
                "./gradlew test"
        );

        RunTestsResponse response = aiOrchestratorClient.runTests(request);

        TestRunResult result = new TestRunResult(
                response.getIncidentId(),
                response.getPatchSuggestionId(),
                response.getStatus(),
                response.getPassed(),
                response.getTestCommand(),
                response.getOutput(),
                response.getDurationMs()
        );

        TestRunResult savedResult = testRunResultRepository.save(result);

        if (isFailedOrErrored(savedResult)) {
            testFailureAnalysisService.analyzeAndSave(savedResult);

        }

        return savedResult;

    }

    public List<TestRunResult> getTestRunsForPatchSuggestion(Long patchSuggestionId) {
        return testRunResultRepository.findByPatchSuggestionId(patchSuggestionId);
    }

    private boolean isFailedOrErrored(TestRunResult result) {
        return "FAILED".equalsIgnoreCase(result.getStatus())
                || "ERROR".equalsIgnoreCase(result.getStatus());
    }
}
