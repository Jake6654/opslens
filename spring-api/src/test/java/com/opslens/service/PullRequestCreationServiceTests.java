package com.opslens.service;

import com.opslens.dto.CreatePullRequestResponse;
import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.github.GitHubApiException;
import com.opslens.github.GitHubBranchReference;
import com.opslens.github.GitHubClient;
import com.opslens.github.GitHubCommitObject;
import com.opslens.github.GitHubPullRequest;
import com.opslens.model.PullRequestRecord;
import com.opslens.repository.PullRequestRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PullRequestCreationServiceTests {

    @Mock
    private PullRequestPreflightService preflightService;

    @Mock
    private PullRequestRecordRepository recordRepository;

    @Mock
    private GitHubClient gitHubClient;

    private PullRequestCreationService creationService;

    @BeforeEach
    void setUp() {
        creationService = new PullRequestCreationService(
                preflightService,
                recordRepository,
                gitHubClient
        );
    }

    @Test
    void returnsLocalRecordWithoutCallingGitHub() {
        PullRequestRecord existingRecord = pullRequestRecord();
        existingRecord.onCreate();

        when(recordRepository.findByPatchSuggestionId(20L))
                .thenReturn(Optional.of(existingRecord));

        CreatePullRequestResponse response =
                creationService.createPullRequest(20L);

        assertFalse(response.isCreated());
        assertEquals(
                "ALREADY_EXISTS",
                response.getOperationStatus()
        );
        assertEquals(42L, response.getPullRequestNumber());

        verifyNoInteractions(preflightService, gitHubClient);
    }

    @Test
    void blocksGitHubCallsWhenPreflightIsNotReady() {
        when(recordRepository.findByPatchSuggestionId(20L))
                .thenReturn(Optional.empty());

        when(preflightService.buildPlan(20L))
                .thenReturn(blockedPlan());

        assertThrows(
                PullRequestBlockedException.class,
                () -> creationService.createPullRequest(20L)
        );

        verifyNoInteractions(gitHubClient);
        verify(recordRepository, never())
                .save(any(PullRequestRecord.class));
    }

    @Test
    void createsDraftPullRequestAndSavesRecord() {
        prepareReadyWorkflow();

        when(gitHubClient.findOpenPullRequest(
                "ai-fix/inc-15-patch-20",
                "main"
        )).thenReturn(Optional.empty());

        when(gitHubClient.createPullRequest(
                "[OpsLens] Fix incident #15",
                "Pull request body",
                "ai-fix/inc-15-patch-20",
                "main",
                true
        )).thenReturn(gitHubPullRequest());

        mockRepositorySave();

        CreatePullRequestResponse response =
                creationService.createPullRequest(20L);

        assertTrue(response.isCreated());
        assertEquals("CREATED", response.getOperationStatus());
        assertEquals(42L, response.getPullRequestNumber());
        assertEquals("OPEN", response.getPullRequestState());
        assertEquals(
                "https://github.com/Jake6654/sketch-my-day/pull/42",
                response.getPullRequestUrl()
        );

        verify(gitHubClient).createPullRequest(
                "[OpsLens] Fix incident #15",
                "Pull request body",
                "ai-fix/inc-15-patch-20",
                "main",
                true
        );

        ArgumentCaptor<PullRequestRecord> recordCaptor =
                ArgumentCaptor.forClass(PullRequestRecord.class);

        verify(recordRepository).save(recordCaptor.capture());

        PullRequestRecord savedRecord = recordCaptor.getValue();

        assertEquals(20L, savedRecord.getPatchSuggestionId());
        assertEquals(15L, savedRecord.getIncidentId());
        assertEquals("patch-sha", savedRecord.getCommitSha());
        assertEquals(42L, savedRecord.getPullRequestNumber());
    }

    @Test
    void restoresDatabaseRecordWhenGitHubPrAlreadyExists() {
        prepareReadyWorkflow();

        when(gitHubClient.findOpenPullRequest(
                "ai-fix/inc-15-patch-20",
                "main"
        )).thenReturn(Optional.of(gitHubPullRequest()));

        mockRepositorySave();

        CreatePullRequestResponse response =
                creationService.createPullRequest(20L);

        assertFalse(response.isCreated());
        assertEquals(
                "ALREADY_EXISTS",
                response.getOperationStatus()
        );

        verify(gitHubClient, never()).createPullRequest(
                any(),
                any(),
                any(),
                any(),
                any(Boolean.class)
        );

        verify(recordRepository)
                .save(any(PullRequestRecord.class));
    }

    @Test
    void rejectsBranchWithoutExpectedCommitMarker() {
        when(recordRepository.findByPatchSuggestionId(20L))
                .thenReturn(Optional.empty());

        when(preflightService.buildPlan(20L))
                .thenReturn(readyPlan());

        when(gitHubClient.findBranch(
                "ai-fix/inc-15-patch-20"
        )).thenReturn(Optional.of(headReference()));

        when(gitHubClient.findBranch("main"))
                .thenReturn(Optional.of(baseReference()));

        GitHubCommitObject unrelatedCommit =
                new GitHubCommitObject(
                        "patch-sha",
                        "tree-sha",
                        "A manually created commit",
                        List.of("base-sha")
                );

        when(gitHubClient.getCommit("patch-sha"))
                .thenReturn(unrelatedCommit);

        assertThrows(
                PullRequestBranchConflictException.class,
                () -> creationService.createPullRequest(20L)
        );

        verify(gitHubClient, never()).findOpenPullRequest(
                any(),
                any()
        );

        verify(gitHubClient, never()).createPullRequest(
                any(),
                any(),
                any(),
                any(),
                any(Boolean.class)
        );
    }

    @Test
    void recoversWhenConcurrentRequestCreatesPullRequest() {
        prepareReadyWorkflow();

        when(gitHubClient.findOpenPullRequest(
                "ai-fix/inc-15-patch-20",
                "main"
        )).thenReturn(
                Optional.empty(),
                Optional.of(gitHubPullRequest())
        );

        when(gitHubClient.createPullRequest(
                "[OpsLens] Fix incident #15",
                "Pull request body",
                "ai-fix/inc-15-patch-20",
                "main",
                true
        )).thenThrow(new GitHubApiException(
                422,
                "A pull request already exists."
        ));

        mockRepositorySave();

        CreatePullRequestResponse response =
                creationService.createPullRequest(20L);

        assertFalse(response.isCreated());
        assertEquals(
                "ALREADY_EXISTS",
                response.getOperationStatus()
        );
        assertEquals(42L, response.getPullRequestNumber());
    }

    private void prepareReadyWorkflow() {
        when(recordRepository.findByPatchSuggestionId(20L))
                .thenReturn(Optional.empty());

        when(preflightService.buildPlan(20L))
                .thenReturn(readyPlan());

        when(gitHubClient.findBranch(
                "ai-fix/inc-15-patch-20"
        )).thenReturn(Optional.of(headReference()));

        when(gitHubClient.findBranch("main"))
                .thenReturn(Optional.of(baseReference()));

        when(gitHubClient.getCommit("patch-sha"))
                .thenReturn(patchCommit());
    }

    private void mockRepositorySave() {
        when(recordRepository.save(
                any(PullRequestRecord.class)
        )).thenAnswer(invocation -> {
            PullRequestRecord record =
                    invocation.getArgument(
                            0,
                            PullRequestRecord.class
                    );

            // Mockito does not run JPA lifecycle callbacks.
            record.onCreate();
            return record;
        });
    }

    private PullRequestPreflightResponse readyPlan() {
        return new PullRequestPreflightResponse(
                20L,
                15L,
                "READY_FOR_PR",
                true,
                "Jake6654/sketch-my-day",
                "main",
                "ai-fix/inc-15-patch-20",
                "[OpsLens] Fix incident #15",
                "Pull request body",
                true,
                true,
                31L,
                "PASSED",
                true,
                List.of()
        );
    }

    private PullRequestPreflightResponse blockedPlan() {
        return new PullRequestPreflightResponse(
                20L,
                15L,
                "TESTS_FAILED",
                false,
                "Jake6654/sketch-my-day",
                "main",
                "ai-fix/inc-15-patch-20",
                "[OpsLens] Fix incident #15",
                "Pull request body",
                true,
                false,
                31L,
                "FAILED",
                true,
                List.of("The latest test run must pass.")
        );
    }

    private GitHubBranchReference headReference() {
        return new GitHubBranchReference(
                "refs/heads/ai-fix/inc-15-patch-20",
                "patch-sha",
                "https://api.github.com/head"
        );
    }

    private GitHubBranchReference baseReference() {
        return new GitHubBranchReference(
                "refs/heads/main",
                "base-sha",
                "https://api.github.com/base"
        );
    }

    private GitHubCommitObject patchCommit() {
        return new GitHubCommitObject(
                "patch-sha",
                "tree-sha",
                "[OpsLens] Apply patch suggestion #20\n\n"
                        + "OpsLens-Patch-Suggestion: 20",
                List.of("base-sha")
        );
    }

    private GitHubPullRequest gitHubPullRequest() {
        return new GitHubPullRequest(
                42L,
                "https://api.github.com/repos/"
                        + "Jake6654/sketch-my-day/pulls/42",
                "https://github.com/"
                        + "Jake6654/sketch-my-day/pull/42",
                "open",
                "[OpsLens] Fix incident #15",
                true,
                false,
                "ai-fix/inc-15-patch-20",
                "patch-sha",
                "main"
        );
    }

    private PullRequestRecord pullRequestRecord() {
        return new PullRequestRecord(
                20L,
                15L,
                "Jake6654/sketch-my-day",
                "main",
                "ai-fix/inc-15-patch-20",
                "patch-sha",
                42L,
                "https://github.com/"
                        + "Jake6654/sketch-my-day/pull/42",
                "OPEN"
        );
    }
}