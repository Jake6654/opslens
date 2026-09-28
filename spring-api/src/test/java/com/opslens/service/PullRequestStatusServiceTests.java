package com.opslens.service;

import com.opslens.dto.PullRequestStatusResponse;
import com.opslens.github.GitHubClient;
import com.opslens.github.GitHubPullRequest;
import com.opslens.model.PullRequestRecord;
import com.opslens.repository.PullRequestRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class PullRequestStatusServiceTests {

    @Mock
    private PullRequestRecordRepository recordRepository;

    @Mock
    private GitHubClient gitHubClient;

    private PullRequestStatusService statusService;

    @BeforeEach
    void setUp() {
        statusService = new PullRequestStatusService(
                recordRepository,
                gitHubClient
        );
    }

    @Test
    void returnsStoredStatusWithoutCallingGitHub() {
        PullRequestRecord record = pullRequestRecord();

        when(recordRepository.findByPatchSuggestionId(20L))
                .thenReturn(Optional.of(record));

        PullRequestStatusResponse response =
                statusService.getStoredStatus(20L);

        assertEquals(20L, response.getPatchSuggestionId());
        assertEquals("OPEN", response.getStatus());
        assertFalse(response.isRefreshedFromGitHub());
        verifyNoInteractions(gitHubClient);
    }

    @Test
    void synchronizesOpenPullRequestFromGitHub() {
        PullRequestRecord record = pullRequestRecord();
        prepareSynchronization(record, remotePullRequest(
                "open",
                false,
                "new-head-sha",
                "ai-fix/inc-15-patch-20"
        ));

        PullRequestStatusResponse response =
                statusService.synchronize(20L);

        assertEquals("OPEN", response.getStatus());
        assertEquals("new-head-sha", response.getCommitSha());
        assertTrue(response.isRefreshedFromGitHub());
        verify(recordRepository).saveAndFlush(record);
    }

    @Test
    void mergedStateTakesPriorityOverClosedState() {
        PullRequestRecord record = pullRequestRecord();
        prepareSynchronization(record, remotePullRequest(
                "closed",
                true,
                "merged-head-sha",
                "ai-fix/inc-15-patch-20"
        ));

        PullRequestStatusResponse response =
                statusService.synchronize(20L);

        assertEquals("MERGED", response.getStatus());
        assertTrue(response.isRefreshedFromGitHub());
    }

    @Test
    void rejectsRecordFromDifferentRepositoryBeforeGitHubLookup() {
        PullRequestRecord record = pullRequestRecord();

        when(recordRepository.findByPatchSuggestionId(20L))
                .thenReturn(Optional.of(record));
        when(gitHubClient.qualifiedRepository())
                .thenReturn("Jake6654/another-project");

        assertThrows(
                PullRequestBranchConflictException.class,
                () -> statusService.synchronize(20L)
        );

        verify(gitHubClient, never()).getPullRequest(any());
        verify(recordRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsGitHubPullRequestWithDifferentBranchIdentity() {
        PullRequestRecord record = pullRequestRecord();

        when(recordRepository.findByPatchSuggestionId(20L))
                .thenReturn(Optional.of(record));
        when(gitHubClient.qualifiedRepository())
                .thenReturn("Jake6654/sketch-my-day");
        when(gitHubClient.getPullRequest(27L))
                .thenReturn(remotePullRequest(
                        "open",
                        false,
                        "unexpected-sha",
                        "manually-moved-branch"
                ));

        assertThrows(
                PullRequestBranchConflictException.class,
                () -> statusService.synchronize(20L)
        );

        assertEquals("original-head-sha", record.getCommitSha());
        verify(recordRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidPatchSuggestionIdBeforeRepositoryLookup() {
        assertThrows(
                IllegalArgumentException.class,
                () -> statusService.getStoredStatus(0L)
        );

        verifyNoInteractions(recordRepository, gitHubClient);
    }

    private void prepareSynchronization(
            PullRequestRecord record,
            GitHubPullRequest remotePullRequest
    ) {
        when(recordRepository.findByPatchSuggestionId(20L))
                .thenReturn(Optional.of(record));
        when(gitHubClient.qualifiedRepository())
                .thenReturn("Jake6654/sketch-my-day");
        when(gitHubClient.getPullRequest(27L))
                .thenReturn(remotePullRequest);
        when(recordRepository.saveAndFlush(record))
                .thenReturn(record);
    }

    private PullRequestRecord pullRequestRecord() {
        PullRequestRecord record = new PullRequestRecord(
                20L,
                15L,
                "Jake6654/sketch-my-day",
                "main",
                "ai-fix/inc-15-patch-20",
                "original-head-sha",
                27L,
                "https://github.com/Jake6654/sketch-my-day/pull/27",
                "OPEN"
        );
        record.onCreate();
        return record;
    }

    private GitHubPullRequest remotePullRequest(
            String state,
            boolean merged,
            String headSha,
            String headBranch
    ) {
        return new GitHubPullRequest(
                27L,
                "https://api.github.com/repos/Jake6654/sketch-my-day/pulls/27",
                "https://github.com/Jake6654/sketch-my-day/pull/27",
                state,
                "[OpsLens] Fix incident #15",
                true,
                merged,
                headBranch,
                headSha,
                "main"
        );
    }
}
