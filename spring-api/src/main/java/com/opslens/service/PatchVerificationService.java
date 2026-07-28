package com.opslens.service;

import com.opslens.dto.PatchVerificationResponse;
import com.opslens.model.PatchSuggestion;
import com.opslens.model.TestRunResult;
import com.opslens.repository.PatchSuggestionRepository;
import com.opslens.repository.TestRunResultRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PatchVerificationService {

    private final PatchSuggestionRepository patchSuggestionRepository;
    private final TestRunResultRepository testRunResultRepository;

    public PatchVerificationService(
            PatchSuggestionRepository patchSuggestionRepository,
            TestRunResultRepository testRunResultRepository
    ) {
        this.patchSuggestionRepository = patchSuggestionRepository;
        this.testRunResultRepository = testRunResultRepository;
    }

    public PatchVerificationResponse verify(Long patchSuggestionId) {
        PatchSuggestion patch = patchSuggestionRepository.findById(patchSuggestionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Patch suggestion not found: " + patchSuggestionId
                ));

        Optional<TestRunResult> latestTestRun =
                testRunResultRepository
                        .findTopByPatchSuggestionIdOrderByCreatedAtDescIdDesc(patchSuggestionId);

        List<String> blockers = new ArrayList<>();

        if (!Boolean.TRUE.equals(patch.getPatchValid())) {
            blockers.add("Patch validation must pass.");
        }

        if (patch.getSuggestedDiff() == null || patch.getSuggestedDiff().isBlank()) {
            blockers.add("A non-empty patch diff is required.");
        }

        if ("HIGH".equalsIgnoreCase(patch.getRiskLevel())) {
            blockers.add("High-risk patches require manual investigation.");
        }

        if (latestTestRun.isEmpty()) {
            blockers.add("Tests have not been run for this patch.");
        } else {
            TestRunResult testRun = latestTestRun.get();
            boolean passed = Boolean.TRUE.equals(testRun.getPassed())
                    && "PASSED".equalsIgnoreCase(testRun.getStatus());

            if (!passed) {
                blockers.add("The latest test run must pass.");
            }
        }

        boolean readyForPullRequest = blockers.isEmpty();
        String status = determineStatus(patch, latestTestRun, readyForPullRequest);

        return new PatchVerificationResponse(
                patch.getId(),
                patch.getIncidentId(),
                status,
                readyForPullRequest,
                patch.getPatchValid(),
                latestTestRun.map(TestRunResult::getPassed).orElse(null),
                latestTestRun.map(TestRunResult::getId).orElse(null),
                latestTestRun.map(TestRunResult::getStatus).orElse(null),
                Boolean.TRUE.equals(patch.getRequiresHumanReview()),
                blockers
        );
    }

    private String determineStatus(
            PatchSuggestion patch,
            Optional<TestRunResult> latestTestRun,
            boolean readyForPullRequest
    ) {
        if (readyForPullRequest) {
            return "READY_FOR_PR";
        }

        if (!Boolean.TRUE.equals(patch.getPatchValid())
                || patch.getSuggestedDiff() == null
                || patch.getSuggestedDiff().isBlank()) {
            return "PATCH_INVALID";
        }

        if ("HIGH".equalsIgnoreCase(patch.getRiskLevel())) {
            return "HIGH_RISK";
        }

        if (latestTestRun.isEmpty()) {
            return "TESTS_NOT_RUN";
        }

        return "TESTS_FAILED";
    }
}
