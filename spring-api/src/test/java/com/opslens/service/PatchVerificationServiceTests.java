package com.opslens.service;

import com.opslens.dto.PatchVerificationResponse;
import com.opslens.model.PatchSuggestion;
import com.opslens.model.TestRunResult;
import com.opslens.repository.PatchSuggestionRepository;
import com.opslens.repository.TestRunResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatchVerificationServiceTests {

    @Mock
    private PatchSuggestionRepository patchSuggestionRepository;

    @Mock
    private TestRunResultRepository testRunResultRepository;

    private PatchVerificationService patchVerificationService;

    @BeforeEach
    void setUp() {
        patchVerificationService = new PatchVerificationService(
                patchSuggestionRepository,
                testRunResultRepository
        );
    }

    @Test
    void marksPatchReadyWhenValidationAndLatestTestsPass() {
        PatchSuggestion patch = patchSuggestion(true, "LOW", "valid diff");
        TestRunResult testRun = testRun("PASSED", true);

        when(patchSuggestionRepository.findById(21L))
                .thenReturn(Optional.of(patch));
        when(testRunResultRepository
                .findTopByPatchSuggestionIdOrderByCreatedAtDescIdDesc(21L))
                .thenReturn(Optional.of(testRun));

        PatchVerificationResponse response =
                patchVerificationService.verify(21L);

        assertTrue(response.isReadyForPullRequest());
        assertEquals("READY_FOR_PR", response.getStatus());
        assertTrue(response.getBlockers().isEmpty());
    }

    @Test
    void blocksPatchWhenValidationFailsAndTestsHaveNotRun() {
        PatchSuggestion patch = patchSuggestion(false, "NEEDS_REVIEW", "");

        when(patchSuggestionRepository.findById(22L))
                .thenReturn(Optional.of(patch));
        when(testRunResultRepository
                .findTopByPatchSuggestionIdOrderByCreatedAtDescIdDesc(22L))
                .thenReturn(Optional.empty());

        PatchVerificationResponse response =
                patchVerificationService.verify(22L);

        assertFalse(response.isReadyForPullRequest());
        assertEquals("PATCH_INVALID", response.getStatus());
        assertEquals(3, response.getBlockers().size());
    }

    private PatchSuggestion patchSuggestion(
            boolean patchValid,
            String riskLevel,
            String suggestedDiff
    ) {
        return new PatchSuggestion(
                15L,
                "Root cause",
                "Patch summary",
                suggestedDiff,
                riskLevel,
                true,
                patchValid,
                null
        );
    }

    private TestRunResult testRun(String status, boolean passed) {
        return new TestRunResult(
                15L,
                21L,
                status,
                passed,
                "./gradlew test",
                "Test output",
                100
        );
    }
}
