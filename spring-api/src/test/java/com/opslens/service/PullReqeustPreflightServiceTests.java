package com.opslens.service;

import com.opslens.config.GitHubProperties;
import com.opslens.dto.PatchVerificationResponse;
import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.model.Incident;
import com.opslens.model.PatchSuggestion;
import com.opslens.repository.IncidentRepository;
import com.opslens.repository.PatchSuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PullRequestPreflightServiceTests {

    @Mock
    private PatchVerificationService patchVerificationService;

    @Mock
    private PatchSuggestionRepository patchSuggestionRepository;

    @Mock
    private IncidentRepository incidentRepository;

    private GitHubProperties githubProperties;
    private PullRequestPreflightService preflightService;

    @BeforeEach
    void setUp() {
        githubProperties = new GitHubProperties();
        githubProperties.setOwner("Jake6654");
        githubProperties.setRepository("sketch-my-day");
        githubProperties.setBaseBranch("main");
        githubProperties.setBranchPrefix("ai-fix");

        preflightService = new PullRequestPreflightService(
                patchVerificationService,
                patchSuggestionRepository,
                incidentRepository,
                githubProperties
        );
    }

    @Test
    void createsReadyPlanWhenPatchAndTestsPass() {
        PatchSuggestion patch = createPatch();
        Incident incident = createIncident();

        when(patchSuggestionRepository.findById(21L))
                .thenReturn(Optional.of(patch));

        when(patchVerificationService.verify(21L))
                .thenReturn(readyVerification());

        when(incidentRepository.findById(15L))
                .thenReturn(Optional.of(incident));

        PullRequestPreflightResponse response =
                preflightService.buildPlan(21L);

        assertTrue(response.isReady());
        assertEquals("READY_FOR_PR", response.getStatus());
        assertEquals(
                "Jake6654/sketch-my-day",
                response.getRepository()
        );
        assertEquals(
                "ai-fix/inc-15-patch-21",
                response.getProposedBranch()
        );
        assertTrue(response.getBlockers().isEmpty());
    }

    @Test
    void blocksPlanWhenVerificationFails() {
        PatchSuggestion patch = createPatch();
        Incident incident = createIncident();

        when(patchSuggestionRepository.findById(21L))
                .thenReturn(Optional.of(patch));

        when(patchVerificationService.verify(21L))
                .thenReturn(blockedVerification());

        when(incidentRepository.findById(15L))
                .thenReturn(Optional.of(incident));

        PullRequestPreflightResponse response =
                preflightService.buildPlan(21L);

        assertFalse(response.isReady());
        assertEquals("TESTS_FAILED", response.getStatus());
        assertEquals(1, response.getBlockers().size());
    }

    @Test
    void blocksPlanWhenGitHubOwnerIsMissing() {
        githubProperties.setOwner("");

        PatchSuggestion patch = createPatch();
        Incident incident = createIncident();

        when(patchSuggestionRepository.findById(21L))
                .thenReturn(Optional.of(patch));

        when(patchVerificationService.verify(21L))
                .thenReturn(readyVerification());

        when(incidentRepository.findById(15L))
                .thenReturn(Optional.of(incident));

        PullRequestPreflightResponse response =
                preflightService.buildPlan(21L);

        assertFalse(response.isReady());
        assertEquals(
                "CONFIGURATION_BLOCKED",
                response.getStatus()
        );
        assertTrue(response.getBlockers().contains(
                "GitHub owner is not configured."
        ));
    }

    @Test
    void throwsWhenPatchSuggestionDoesNotExist() {
        when(patchSuggestionRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> preflightService.buildPlan(999L)
        );
    }

    private PatchSuggestion createPatch() {
        return new PatchSuggestion(
                15L,
                "A required field was null.",
                "Add request validation.",
                "--- a/File.java\n+++ b/File.java",
                "LOW",
                true,
                true,
                "Patch is valid."
        );
    }

    private Incident createIncident() {
        return new Incident(
                10L,
                "sketch-my-day",
                "dev",
                "DiaryService",
                "ERROR",
                "OPEN",
                "Diary save validation failure"
        );
    }

    private PatchVerificationResponse readyVerification() {
        return new PatchVerificationResponse(
                21L,
                15L,
                "READY_FOR_PR",
                true,
                true,
                true,
                31L,
                "PASSED",
                true,
                List.of()
        );
    }

    private PatchVerificationResponse blockedVerification() {
        return new PatchVerificationResponse(
                21L,
                15L,
                "TESTS_FAILED",
                false,
                true,
                false,
                31L,
                "FAILED",
                true,
                List.of("The latest test run must pass.")
        );
    }
}