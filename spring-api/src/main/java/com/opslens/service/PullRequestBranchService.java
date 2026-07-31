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

    public CreatePullRequestBranchResponse createBranch(
            Long patchSuggestionId
    ) {
        PullRequestPreflightResponse plan =
                preflightService.buildPlan(patchSuggestionId);

        if (!plan.isReady()) {
            throw new PullRequestBlockedException(plan.getBlockers());
        }

        GitHubBranchReference baseReference = gitHubClient
                .findBranch(plan.getBaseBranch())
                .orElseThrow(() -> new GitHubApiException(
                        404,
                        "GitHub base branch was not found: "
                                + plan.getBaseBranch()
                ));

        Optional<GitHubBranchReference> existing =
                gitHubClient.findBranch(plan.getProposedBranch());

        if (existing.isPresent()) {
            return resolveExistingBranch(
                    plan,
                    baseReference,
                    existing.get()
            );
        }

        try {
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
            if (error.getStatusCode() != 422) {
                throw error;
            }

            return resolveCreateRace(plan, baseReference, error);
        }
    }

    private CreatePullRequestBranchResponse resolveExistingBranch(
            PullRequestPreflightResponse plan,
            GitHubBranchReference baseReference,
            GitHubBranchReference existing
    ) {
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

    private CreatePullRequestBranchResponse resolveCreateRace(
            PullRequestPreflightResponse plan,
            GitHubBranchReference baseReference,
            GitHubApiException originalError
    ) {
        Optional<GitHubBranchReference> existing =
                gitHubClient.findBranch(plan.getProposedBranch());

        if (existing.isEmpty()) {
            throw originalError;
        }

        return resolveExistingBranch(
                plan,
                baseReference,
                existing.get()
        );
    }

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