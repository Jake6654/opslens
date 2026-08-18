package com.opslens.service;

import com.opslens.dto.CreatePullRequestCommitResponse;
import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.github.*;
import com.opslens.model.PatchSuggestion;
import com.opslens.repository.PatchSuggestionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PullRequestCommitService {
    private static final String COMMIT_PREFIX =
            "[OpsLens] Apply patch suggestion #";

    private final PullRequestPreflightService preflightService;
    private final PatchSuggestionRepository patchSuggestionRepository;
    private final UnifiedDiffMaterializer diffMaterializer;
    private final GitHubClient gitHubClient;

    public PullRequestCommitService(
            PullRequestPreflightService preflightService,
            PatchSuggestionRepository patchSuggestionRepository,
            UnifiedDiffMaterializer diffMaterializer,
            GitHubClient gitHubClient
    ) {
        this.preflightService = preflightService;
        this.patchSuggestionRepository = patchSuggestionRepository;
        this.diffMaterializer = diffMaterializer;
        this.gitHubClient = gitHubClient;
    }

    /**
     * Materializes and commits a validated patch to its AI-created branch
     */
    public CreatePullRequestCommitResponse createCommit(
            Long patchSuggestionId
    ) {
        // Safety state can change, so verify it immediately before mutation.
        PullRequestPreflightResponse plan =
                preflightService.buildPlan(patchSuggestionId);

        if (!plan.isReady()) {
            throw new PullRequestBlockedException(plan.getBlockers());
        }

        PatchSuggestion patch = patchSuggestionRepository
                .findById(patchSuggestionId)
                .orElseThrow(() -> new PullRequestBranchConflictException("Patch suggestion not found: " + patchSuggestionId
                ));

        GitHubBranchReference branchReference = gitHubClient
                .findBranch(plan.getProposedBranch())
                .orElseThrow(() -> new PullRequestBranchConflictException(
                        "The AI branch does not exist. Create it before "
                                + "creating a commit."
                ));

        GitHubCommitObject branchHead =
                gitHubClient.getCommit(branchReference.sha());

        String commitMarker = commitMarker(patchSuggestionId);

        // This makes retires idempotent after a successful commit.
        if (branchHead.message().contains(commitMarker)){
            return alreadyCommitted(plan, branchHead, patch);
        }

        GitHubBranchReference baseReference = gitHubClient
                .findBranch(plan.getBaseBranch())
                .orElseThrow(() -> new PullRequestBranchConflictException(
                        "The configured base branch does not exist."
                ));

        // The branch must still be untouched since Phase 5B created it.
        if (!branchReference.sha().equals(baseReference.sha())) {
            throw new PullRequestBranchConflictException(
                    "The AI branch moved before OpsLens created its patch "
                            + "commit and will not be overwritten."
            );
        }

        String targetPath =
                diffMaterializer.targetPath(patch.getSuggestedDiff());

        // Read the exact file version represented by the branch SHA.
        GitHubFileContent originalFile = gitHubClient.getFile(
                targetPath,
                branchReference.sha()
        );

        MaterializedPatchFile patchedFile = diffMaterializer.apply(
                patch.getSuggestedDiff(),
                originalFile
        );

        GitHubTreeReference tree = gitHubClient.createTree(
                branchHead.treeSha(),
                patchedFile
        );

        GitHubCommitObject commit = gitHubClient.createCommit(
                buildCommitMessage(patchSuggestionId, patch),
                tree.sha(),
                branchReference.sha()
        );

        // Re-read the branch just before moving it to narrow the race window.
        GitHubBranchReference latestReference = gitHubClient
                .findBranch(plan.getProposedBranch())
                .orElseThrow(() -> new PullRequestBranchConflictException(
                        "The AI branch disappeared during commit creation."
                ));

        if (!latestReference.sha().equals(branchReference.sha())) {
            throw new PullRequestBranchConflictException(
                    "The AI branch changed during commit creation and will "
                            + "not be overwritten."
            );
        }

        // force=false in GitHubClient provides the final fast-forward guard.
        GitHubBranchReference updated = gitHubClient.updateBranch(
                plan.getProposedBranch(),
                commit.sha()
        );

        return new CreatePullRequestCommitResponse(
                patchSuggestionId,
                plan.getIncidentId(),
                plan.getRepository(),
                plan.getProposedBranch(),
                branchReference.sha(),
                updated.sha(),
                "CREATED",
                true,
                List.of(patchedFile.path())
        );
    }

    private CreatePullRequestCommitResponse alreadyCommitted(
            PullRequestPreflightResponse plan,
            GitHubCommitObject branchHead,
            PatchSuggestion patch
    ) {
        String previousSha = branchHead.parentShas().isEmpty()
                ? ""
                : branchHead.parentShas().getFirst();

        return new CreatePullRequestCommitResponse(
                plan.getPatchSuggestionId(),
                plan.getIncidentId(),
                plan.getRepository(),
                plan.getProposedBranch(),
                previousSha,
                branchHead.sha(),
                "ALREADY_COMMITTED",
                false,
                List.of(diffMaterializer.targetPath(
                        patch.getSuggestedDiff()
                ))
        );
    }

    private String buildCommitMessage(
            Long patchSuggestionId,
            PatchSuggestion patch
    ) {
        return COMMIT_PREFIX
                + patchSuggestionId
                + "\n\n"
                + safeSummary(patch.getPatchSummary())
                + "\n\n"
                + commitMarker(patchSuggestionId);
    }

    private String commitMarker(Long patchSuggestionId) {
        return "OpsLens-Patch-Suggestion: " + patchSuggestionId;
    }

    private String safeSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return "Apply the validated OpsLens patch suggestion.";
        }

        return summary.trim();
    }
}