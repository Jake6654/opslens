package com.opslens.service;

import com.opslens.dto.PullRequestStatusResponse;
import com.opslens.github.GitHubClient;
import com.opslens.github.GitHubPullRequest;
import com.opslens.model.PullRequestRecord;
import com.opslens.repository.PullRequestRecordRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class PullRequestStatusService {

    private final PullRequestRecordRepository recordRepository;
    private final GitHubClient gitHubClient;

    public PullRequestStatusService(PullRequestRecordRepository recordRepository, GitHubClient gitHubClient) {
        this.recordRepository = recordRepository;
        this.gitHubClient = gitHubClient;
    }

    /**
     * Returns the pull request state currently stored in the database
     * This method does not call GitHub
     */
    public PullRequestStatusResponse getStoredStatus(
            Long patchSuggestionId
    ) {
       PullRequestRecord record = findRecord(patchSuggestionId);

       return toResponse(record, false);
    }

    /**
     * Reads the latest pull request state from GitHub and stores it
     * in PostgreSQL
     */
    public PullRequestStatusResponse synchronize(
            Long patchSuggestionId
    ) {
        PullRequestRecord record = findRecord(patchSuggestionId);

        validateRepository(record);

        GitHubPullRequest remotePullRequest =
                gitHubClient.getPullRequest(
                        record.getPullRequestNumber()
                );

        validateIdentity(record, remotePullRequest);

        String latestStatus = resolveStatus(remotePullRequest);

        record.synchronizeGitHubState(
                latestStatus,
                remotePullRequest.headSha()
        );

        PullRequestRecord savedRecord =
                // saveAndFlush 는 변경 내용을 즉시 DB에 반영한다
                recordRepository.saveAndFlush(record);

        return toResponse(savedRecord, true);
    }

    /**
     * Finds the local PR record for one patch suggestion
     */
    private PullRequestRecord findRecord(
            Long patchSuggestionId
    ) {
        if (patchSuggestionId == null || patchSuggestionId <= 0) {
            throw new IllegalArgumentException(
                    "A valid patch suggestion ID is required."
            );
        }

        return recordRepository
                .findByPatchSuggestionId(patchSuggestionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pull request record not found for patch "
                                + "suggestion: "
                                + patchSuggestionId
                ));
    }

    /**
     * Prevents a record created for another repository from being
     * synchronized through the configured GitHub Client.
     */
    private void validateRepository(
            PullRequestRecord record
    ) {
        String configuredRepository =
                gitHubClient.qualifiedRepository();

        // DB record 는 repo-a 인데 GitHubClient 가 repo-b 로 설정됐다면, 같은
        // PR 번호가 다른 repo 를 의미할수있다
        if (!record.getRepository().equals(configuredRepository)) {
            throw new PullRequestBranchConflictException(
                    "The stored pull request belongs to a different "
                            + "GitHub repository."
            );
        }
    }

    /**
     * Confirms that GitHub returned the PR represented by the local record.
     */
    private void validateIdentity(
            PullRequestRecord record,
            GitHubPullRequest remotePullRequest
    ) {
        boolean numberMatches =
                record.getPullRequestNumber().equals(
                        remotePullRequest.number()
                );

        boolean headMatches =
                record.getHeadBranch().equals(
                        remotePullRequest.headBranch()
                );

        boolean baseMatches =
                record.getBaseBranch().equals(
                        remotePullRequest.baseBranch()
                );

        if (!numberMatches || !headMatches || !baseMatches) {
            throw new PullRequestBranchConflictException(
                    "The GitHub pull request does not match the "
                            + "stored OpsLens pull request record."
            );
        }
    }

    /**
     * Converts GitHub's state into the OpsLens status vocabulary.
     */
    private String resolveStatus(
            GitHubPullRequest pullRequest
    ) {
        if (pullRequest.merged()) {
            return "MERGED";
        }

        String state = pullRequest.state();

        if (state == null || state.isBlank()) {
            return "UNKNOWN";
        }

        String normalized =
                state.trim().toUpperCase(Locale.ROOT);

        if ("OPEN".equals(normalized)
                || "CLOSED".equals(normalized)) {
            return normalized;
        }

        return "UNKNOWN";
    }

    /**
     * Converts the persistence entity into the public API response.
     */
    private PullRequestStatusResponse toResponse(
            PullRequestRecord record,
            boolean refreshedFromGitHub
    ) {
        return new PullRequestStatusResponse(
                record.getPatchSuggestionId(),
                record.getIncidentId(),
                record.getRepository(),
                record.getBaseBranch(),
                record.getHeadBranch(),
                record.getCommitSha(),
                record.getPullRequestNumber(),
                record.getPullRequestUrl(),
                record.getStatus(),
                refreshedFromGitHub,
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }


}
