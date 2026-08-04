package com.opslens.service;

import com.opslens.dto.CreatePullRequestBranchResponse;
import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.github.GitHubApiException;
import com.opslens.github.GitHubBranchReference;
import com.opslens.github.GitHubClient;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PullRequestBranchService {

    private final PullRequestPreflightService preflightService;
    private final GitHubClient gitHubClient;

    public PullRequestBranchService(
            PullRequestPreflightService preflightService,
            GitHubClient gitHubClient
    ) {
        this.preflightService = preflightService;
        this.gitHubClient = gitHubClient;
    }

    /**
     * Creates the planned pull-request branch only when preflight checks pass.
     * Repeating the same safe request returns ALREADY_EXISTS instead of
     * creating or overwriting another branch.
     */
    public CreatePullRequestBranchResponse createBranch(
            Long patchSuggestionId
    ) {
        // Re-run preflight immediately before mutating GitHub state.
        PullRequestPreflightResponse plan =
                preflightService.buildPlan(patchSuggestionId);

        // Unsafe patches or failed tests must never reach the GitHub client.
        if (!plan.isReady()) {
            throw new PullRequestBlockedException(plan.getBlockers());
        }

        // The new branch must begin at the current base branch commit.
        GitHubBranchReference baseReference = gitHubClient
                .findBranch(plan.getBaseBranch())
                .orElseThrow(() -> new GitHubApiException(
                        404,
                        "GitHub base branch was not found: "
                                + plan.getBaseBranch()
                ));

        Optional<GitHubBranchReference> existing =
                gitHubClient.findBranch(plan.getProposedBranch());

        // Resolve an existing branch without overwriting it.
        if (existing.isPresent()) {
            return resolveExistingBranch(
                    plan,
                    baseReference,
                    existing.get()
            );
        }

        try {
            // Create the branch at the exact base SHA selected above.
            GitHubBranchReference created = gitHubClient.createBranch(
                    plan.getProposedBranch(),
                    baseReference.sha()
            );

            return response(
                    plan,
                    baseReference,
                    created,
                    "CREATED",
                    true
            );
        } catch (GitHubApiException error) {
            // GitHub commonly returns 422 when another request created the ref.
            if (error.getStatusCode() != 422) {
                throw error;
            }

            return resolveCreateRace(plan, baseReference, error);
        }
    }

    /**
     * Treats an existing branch as idempotent only when it still points to the
     * expected base SHA; otherwise it reports a conflict and preserves it.
     */
    private CreatePullRequestBranchResponse resolveExistingBranch(
            PullRequestPreflightResponse plan,
            GitHubBranchReference baseReference,
            GitHubBranchReference existing
    ) {
        // Never overwrite a branch that may contain someone else's commits.
        if (!baseReference.sha().equals(existing.sha())) {
            throw new PullRequestBranchConflictException(
                    "The proposed branch already exists at a different "
                            + "commit and will not be overwritten."
            );
        }

        return response(
                plan,
                baseReference,
                existing,
                "ALREADY_EXISTS",
                false
        );
    }

    /**
     * Recovers from a concurrent branch-creation race by reading the branch
     * again and applying the same SHA safety check.
     */
    private CreatePullRequestBranchResponse resolveCreateRace(
            PullRequestPreflightResponse plan,
            GitHubBranchReference baseReference,
            GitHubApiException originalError
    ) {
        Optional<GitHubBranchReference> existing =
                gitHubClient.findBranch(plan.getProposedBranch());

        // If no branch appeared, the original GitHub error was not a safe race.
        if (existing.isEmpty()) {
            throw originalError;
        }

        return resolveExistingBranch(
                plan,
                baseReference,
                existing.get()
        );
    }

    /**
     * Maps internal plan and GitHub reference data to the public API response.
     */
    private CreatePullRequestBranchResponse response(
            PullRequestPreflightResponse plan,
            GitHubBranchReference baseReference,
            GitHubBranchReference branchReference,
            String status,
            boolean created
    ) {
        return new CreatePullRequestBranchResponse(
                plan.getPatchSuggestionId(),
                plan.getIncidentId(),
                plan.getRepository(),
                plan.getBaseBranch(),
                plan.getProposedBranch(),
                baseReference.sha(),
                branchReference.sha(),
                status,
                created
        );
    }
}
