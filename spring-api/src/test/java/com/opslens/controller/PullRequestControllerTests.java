package com.opslens.controller;

import com.opslens.dto.PullRequestStatusResponse;
import com.opslens.github.GitHubApiException;
import com.opslens.service.PullRequestBranchConflictException;
import com.opslens.service.PullRequestBranchService;
import com.opslens.service.PullRequestCommitService;
import com.opslens.service.PullRequestCreationService;
import com.opslens.service.PullRequestPreflightService;
import com.opslens.service.PullRequestStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PullRequestControllerTests {

    @Mock
    private PullRequestPreflightService preflightService;

    @Mock
    private PullRequestBranchService branchService;

    @Mock
    private PullRequestCommitService commitService;

    @Mock
    private PullRequestCreationService creationService;

    @Mock
    private PullRequestStatusService statusService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PullRequestController controller = new PullRequestController(
                preflightService,
                branchService,
                commitService,
                creationService,
                statusService
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void returnsStoredPullRequestStatus() throws Exception {
        when(statusService.getStoredStatus(20L))
                .thenReturn(statusResponse(false));

        mockMvc.perform(get(
                        "/patch-suggestions/20/pull-request/status"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patchSuggestionId").value(20))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.refreshedFromGitHub").value(false));
    }

    @Test
    void refreshesPullRequestStatusFromGitHub() throws Exception {
        when(statusService.synchronize(20L))
                .thenReturn(statusResponse(true));

        mockMvc.perform(post(
                        "/patch-suggestions/20/pull-request/status/refresh"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pullRequestNumber").value(27))
                .andExpect(jsonPath("$.refreshedFromGitHub").value(true));
    }

    @Test
    void returnsNotFoundWhenLocalRecordDoesNotExist() throws Exception {
        when(statusService.getStoredStatus(999L))
                .thenThrow(new IllegalArgumentException(
                        "Pull request record not found."
                ));

        mockMvc.perform(get(
                        "/patch-suggestions/999/pull-request/status"
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void returnsConflictWhenGitHubIdentityDoesNotMatch() throws Exception {
        when(statusService.synchronize(20L))
                .thenThrow(new PullRequestBranchConflictException(
                        "GitHub pull request identity does not match."
                ));

        mockMvc.perform(post(
                        "/patch-suggestions/20/pull-request/status/refresh"
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void returnsBadGatewayWhenGitHubRequestFails() throws Exception {
        when(statusService.synchronize(20L))
                .thenThrow(new GitHubApiException(
                        403,
                        "GitHub rejected the request."
                ));

        mockMvc.perform(post(
                        "/patch-suggestions/20/pull-request/status/refresh"
                ))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Bad Gateway"));
    }

    private PullRequestStatusResponse statusResponse(
            boolean refreshedFromGitHub
    ) {
        LocalDateTime timestamp = LocalDateTime.of(
                2026,
                9,
                28,
                15,
                0
        );

        return new PullRequestStatusResponse(
                20L,
                15L,
                "Jake6654/sketch-my-day",
                "main",
                "ai-fix/inc-15-patch-20",
                "42a44b2f63af6d79b254dc661fb502c00fe7155e",
                27L,
                "https://github.com/Jake6654/sketch-my-day/pull/27",
                "OPEN",
                refreshedFromGitHub,
                timestamp,
                timestamp
        );
    }
}
