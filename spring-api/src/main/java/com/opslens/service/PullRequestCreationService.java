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
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class PullRequestCreationService {

    private static final String COMMIT_MARKER_PREFIX = "OpsLens-Patch-Suggestion: ";

    // 현재 patch 가 PR 생성 조건을 만족하는지 검사
    private final PullRequestPreflightService preflightService;
    private final PullRequestRecordRepository recordRepository;
    private final GitHubClient gitHubClient;


    public PullRequestCreationService(PullRequestPreflightService preflightService, PullRequestRecordRepository recordRepository, GitHubClient gitHubClient) {
        this.preflightService = preflightService;
        this.recordRepository = recordRepository;
        this.gitHubClient = gitHubClient;
    }

    /**
     * Creates a draft pull request for a validated patch suggestions.
     *
     * The method is idempotent:
     * repeated requests return the existing pull request instead of
     * creating another one
     */
    public CreatePullRequestResponse createPullRequest(
            Long patchSuggestionId
    ) {
        validatePatchSuggestionId(patchSuggestionId);

        // Check the local database before calling GitHub
        Optional<PullRequestRecord> localRecord = recordRepository
                .findByPatchSuggestionId(patchSuggestionId);


        if (localRecord.isPresent()) {
            return toResponse(
                    localRecord.get(),
                    "ALREADY_EXISTS",
                    false
            );
        }

        // Re-run all safety checks before changing GitHub
        PullRequestPreflightResponse plan = preflightService.buildPlan(patchSuggestionId);

        if (!plan.isReady()) {
            throw new PullRequestBlockedException(plan.getBlockers());
        }

        // The Phase 5B branch must already exist
        GitHubBranchReference headReference = gitHubClient.findBranch(plan.getProposedBranch())
                .orElseThrow(() -> new PullRequestBranchConflictException(
                        "The AI branch does not exist."
                                + "Create the branch first"
                ));

        GitHubBranchReference baseReference =
                gitHubClient.findBranch(plan.getBaseBranch())
                        .orElseThrow(() ->
                                new PullRequestBranchConflictException(
                                        "The configured base branch "
                                                + "does not exist."
                                )
                        );

        // A PR without a patch commit would contain no actual change.
        // SHA 가 같다면 두 branch가 같은 커밋을 가리키고 있다는 뜻이다
        if (headReference.sha().equals(baseReference.sha())) {
            throw new PullRequestBranchConflictException(
                    "The AI branch does not contain a patch commit." +
                            "Create the commit before creating the PR"
            );
        }

        GitHubCommitObject branchHead = gitHubClient.getCommit(headReference.sha());

        requireExpectedPatchCommit(
                patchSuggestionId,
                branchHead
        );

        // GitHub is checked because the DB could have lost or missed a record.
        Optional<GitHubPullRequest> existingPullRequest =
                gitHubClient.findOpenPullRequest(
                        plan.getProposedBranch(),
                        plan.getBaseBranch()
                );

        if (existingPullRequest.isPresent()) {
            GitHubPullRequest pullRequest =
                    existingPullRequest.get();

            validatePullRequest(
                    plan,
                    headReference,
                    pullRequest
            );

            PullRequestRecord savedRecord = saveRecord(
                    plan,
                    pullRequest
            );

            return toResponse(
                    savedRecord,
                    "ALREADY_EXISTS",
                    false
            );
        }

        GitHubPullRequest createdPullRequest;

        try {
            createdPullRequest = gitHubClient.createPullRequest(
                    plan.getTitle(),
                    plan.getBody(),
                    plan.getProposedBranch(),
                    plan.getBaseBranch(),
                    true
            );

        } catch (GitHubApiException error) {
            return  recoverFromCreationRace(
                    error,
                    plan,
                    headReference
            );
        }

        validatePullRequest(
                plan,
                headReference,
                createdPullRequest
        );

        PullRequestRecord savedRecord = saveRecord(
                plan,
                createdPullRequest
        );

        return toResponse(
                savedRecord,
                "CREATED",
                true
        );
    }

    /**
     * Rejects invalid IDs before repository or GitHub calls are made.
     */
    private void validatePatchSuggestionId(Long patchSuggestionId) {
        if (patchSuggestionId == null || patchSuggestionId <= 0) {
            throw new IllegalArgumentException(
                    "A valid patch suggestion ID is required."
            );
        }

    }

    /**
     * Verifies that the branch head was created for this patch suggestion.
     */
    private void requireExpectedPatchCommit(
            Long patchSuggestionId,
            GitHubCommitObject branchHead
    ) {
        String expectedMarker =
                COMMIT_MARKER_PREFIX + patchSuggestionId;

        if (branchHead.message() == null
        || !branchHead.message().contains(expectedMarker)) {
            throw new PullRequestBranchConflictException(
                    "The AI branch does not point to the expected"
                    + "OpsLens patch commit."
            );
        }
    }

    /**
     * Ensures the GitHub response matches the branch and commit that
     * OpsLens verified.
     */
    private void validatePullRequest(
            PullRequestPreflightResponse plan,
            GitHubBranchReference headReference,
            GitHubPullRequest pullRequest
    ) {
        boolean branchMatches =
                plan.getProposedBranch().equals(
                        pullRequest.headBranch()
                );

        boolean baseMatches =
                plan.getBaseBranch().equals(
                        pullRequest.baseBranch()
                );

        boolean commitMatches =
                headReference.sha().equals(
                        pullRequest.headSha()
                );

        if (!branchMatches || !baseMatches || !commitMatches) {
            throw new PullRequestBranchConflictException(
                    "GitHub returned a pull request that does not "
                            + "match the verified branch state."
            );
        }
    }

    /**
     * Saves GitHub's pull request identity in PostgreSQL.
     */
    private PullRequestRecord saveRecord(
            PullRequestPreflightResponse plan,
            GitHubPullRequest pullRequest
    ) {
        PullRequestRecord record = new PullRequestRecord(
                plan.getPatchSuggestionId(),
                plan.getIncidentId(),
                plan.getRepository(),
                plan.getBaseBranch(),
                plan.getProposedBranch(),
                pullRequest.headSha(),
                pullRequest.number(),
                pullRequest.htmlUrl(),
                normalizeState(pullRequest.state())
        );

        return recordRepository.save(record);
    }

    /**
     * Handles two simultaneous requests attempting to create the same PR.
     */
    private CreatePullRequestResponse recoverFromCreationRace(
            GitHubApiException originalError,
            PullRequestPreflightResponse plan,
            GitHubBranchReference headReference
    ) {
        if (originalError.getStatusCode() != 422) {
            throw originalError;
        }

        Optional<GitHubPullRequest> existing =
                gitHubClient.findOpenPullRequest(
                        plan.getProposedBranch(),
                        plan.getBaseBranch()
                );

        if (existing.isEmpty()) {
            throw originalError;
        }

        GitHubPullRequest pullRequest = existing.get();

        validatePullRequest(
                plan,
                headReference,
                pullRequest
        );

        PullRequestRecord savedRecord =
                saveRecord(plan, pullRequest);

        return toResponse(
                savedRecord,
                "ALREADY_EXISTS",
                false
        );
    }

    /**
     * Converts a database entity into the public API response DTO.
     */
    private CreatePullRequestResponse toResponse(
            PullRequestRecord record,
            String operationStatus,
            boolean created
    ) {
        return new CreatePullRequestResponse(
                record.getPatchSuggestionId(),
                record.getIncidentId(),
                record.getRepository(),
                record.getBaseBranch(),
                record.getHeadBranch(),
                record.getCommitSha(),
                record.getPullRequestNumber(),
                record.getPullRequestUrl(),
                record.getStatus(),
                operationStatus,
                created,
                record.getCreatedAt()
        );
    }

    private String normalizeState(String state) {
        if (state == null || state.isBlank()) {
            return "UNKNOWN";
        }

        return state.trim().toUpperCase(Locale.ROOT);
    }
}




