package com.opslens.service;

import com.opslens.dto.CreatePullRequestBranchResponse;
import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.github.GitHubBranchReference;
import com.opslens.github.GitHubClient;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PullRequestBranchServiceTests {

    @Mock
    private PullRequestPreflightService preflightService;

    @Mock
    private GitHubClient gitHubClient;

    private PullRequestBranchService branchService;

    @BeforeEach
    void setUp() {
        branchService = new PullRequestBranchService(
                preflightService,
                gitHubClient
        );
    }

    @Test
    void createsBranchFromBaseShaWhenPreflightIsReady() {
        PullRequestPreflightResponse plan = readyPlan();
        GitHubBranchReference base = reference(
                "refs/heads/main",
                "base-sha"
        );
        GitHubBranchReference created = reference(
                "refs/heads/ai-fix/inc-15-patch-20",
                "base-sha"
        );

        when(preflightService.buildPlan(20L)).thenReturn(plan);
        when(gitHubClient.findBranch("main"))
                .thenReturn(Optional.of(base));
        when(gitHubClient.findBranch(
                "ai-fix/inc-15-patch-20"
        )).thenReturn(Optional.empty());
        when(gitHubClient.createBranch(
                "ai-fix/inc-15-patch-20",
                "base-sha"
        )).thenReturn(created);

        CreatePullRequestBranchResponse response =
                branchService.createBranch(20L);

        assertTrue(response.isCreated());
        assertEquals("CREATED", response.getStatus());
        assertEquals("base-sha", response.getBranchCommitSha());
    }

    @Test
    void blocksGitHubCallsWhenPreflightIsNotReady() {
        when(preflightService.buildPlan(20L))
                .thenReturn(blockedPlan());

        assertThrows(
                PullRequestBlockedException.class,
                () -> branchService.createBranch(20L)
        );

        verifyNoInteractions(gitHubClient);
    }

    @Test
    void returnsAlreadyExistsWhenBranchHasExpectedSha() {
        PullRequestPreflightResponse plan = readyPlan();
        GitHubBranchReference base = reference(
                "refs/heads/main",
                "base-sha"
        );
        GitHubBranchReference existing = reference(
                "refs/heads/ai-fix/inc-15-patch-20",
                "base-sha"
        );

        when(preflightService.buildPlan(20L)).thenReturn(plan);
        when(gitHubClient.findBranch("main"))
                .thenReturn(Optional.of(base));
        when(gitHubClient.findBranch(
                "ai-fix/inc-15-patch-20"
        )).thenReturn(Optional.of(existing));

        CreatePullRequestBranchResponse response =
                branchService.createBranch(20L);

        assertFalse(response.isCreated());
        assertEquals("ALREADY_EXISTS", response.getStatus());
        verify(gitHubClient, never()).createBranch(
                "ai-fix/inc-15-patch-20",
                "base-sha"
        );
    }

    @Test
    void blocksExistingBranchAtDifferentSha() {
        PullRequestPreflightResponse plan = readyPlan();

        when(preflightService.buildPlan(20L)).thenReturn(plan);
        when(gitHubClient.findBranch("main"))
                .thenReturn(Optional.of(reference(
                        "refs/heads/main",
                        "base-sha"
                )));
        when(gitHubClient.findBranch(
                "ai-fix/inc-15-patch-20"
        )).thenReturn(Optional.of(reference(
                "refs/heads/ai-fix/inc-15-patch-20",
                "different-sha"
        )));

        assertThrows(
                PullRequestBranchConflictException.class,
                () -> branchService.createBranch(20L)
        );
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
                15L,
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
                15L,
                "FAILED",
                true,
                List.of("The latest test run must pass.")
        );
    }

    private GitHubBranchReference reference(
            String ref,
            String sha
    ) {
        return new GitHubBranchReference(
                ref,
                sha,
                "https://api.github.com/reference"
        );
    }
}