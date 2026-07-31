package com.opslens.service;

import com.opslens.config.GitHubProperties;
import com.opslens.dto.PatchVerificationResponse;
import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.model.Incident;
import com.opslens.model.PatchSuggestion;
import com.opslens.repository.IncidentRepository;
import com.opslens.repository.PatchSuggestionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PullRequestPreflightService {

    private static final int MAX_TITLE_LENGTH = 100;

    private final PatchVerificationService patchVerificationService;
    private final PatchSuggestionRepository patchSuggestionRepository;
    private final IncidentRepository incidentRepository;
    private final GitHubProperties githubProperties;

    public PullRequestPreflightService(
            PatchVerificationService patchVerificationService,
            PatchSuggestionRepository patchSuggestionRepository,
            IncidentRepository incidentRepository,
            GitHubProperties githubProperties
    ) {
        this.patchVerificationService = patchVerificationService;
        this.patchSuggestionRepository = patchSuggestionRepository;
        this.incidentRepository = incidentRepository;
        this.githubProperties = githubProperties;
    }

    public PullRequestPreflightResponse buildPlan(Long patchSuggestionId) {
        // Loading the patch
        PatchSuggestion patch = patchSuggestionRepository
                .findById(patchSuggestionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Patch suggestion not found: " + patchSuggestionId
                ));

        // reusing verification method we created at Phase 4
        PatchVerificationResponse verification =
                patchVerificationService.verify(patchSuggestionId);

        Incident incident = incidentRepository
                .findById(patch.getIncidentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Incident not found: " + patch.getIncidentId()
                ));

        // verification.getBlockers() is immutable. The service creates a mutable copy:
        // we need to add blockers, so make it mutable
        List<String> blockers =
                new ArrayList<>(verification.getBlockers());

        String owner = normalizeValue(githubProperties.getOwner());
        String repositoryName =
                normalizeValue(githubProperties.getRepository());
        String baseBranch =
                normalizeValue(githubProperties.getBaseBranch());
        String branchPrefix =
                normalizeBranchPrefix(githubProperties.getBranchPrefix());

        validateConfiguration(
                owner,
                repositoryName,
                baseBranch,
                branchPrefix,
                blockers
        );

        String qualifiedRepository =
                buildQualifiedRepository(owner, repositoryName);


        String proposedBranch = buildBranchName(
                branchPrefix,
                patch.getIncidentId(),
                patchSuggestionId
        );

        if (isProtectedBranch(proposedBranch)) {
            blockers.add(
                    "The proposed branch must not be a protected branch."
            );
        }

        String title = buildTitle(incident);
        String body = buildBody(
                incident,
                patch,
                verification,
                proposedBranch
        );

        boolean ready =
                verification.isReadyForPullRequest()
                        && blockers.isEmpty();

        String status;

        if (ready) {
            status = "READY_FOR_PR";
        } else if (verification.isReadyForPullRequest()) {
            status = "CONFIGURATION_BLOCKED";
        } else {
            status = verification.getStatus();
        }

        return new PullRequestPreflightResponse(
                patchSuggestionId,
                patch.getIncidentId(),
                status,
                ready,
                qualifiedRepository,
                baseBranch,
                proposedBranch,
                title,
                body,
                verification.getPatchValid(),
                verification.getTestsPassed(),
                verification.getLatestTestRunId(),
                verification.getLatestTestStatus(),
                verification.isRequiresHumanReview(),
                blockers
        );
    }

    private void validateConfiguration(
            String owner,
            String repository,
            String baseBranch,
            String branchPrefix,
            List<String> blockers
    ) {
        if (owner.isBlank()) {
            blockers.add("GitHub owner is not configured.");
        }

        if (repository.isBlank()) {
            blockers.add("GitHub repository is not configured.");
        }

        if (baseBranch.isBlank()) {
            blockers.add("GitHub base branch is not configured.");
        }

        if (branchPrefix.isBlank()) {
            blockers.add("GitHub branch prefix is not configured.");
        }
    }

    private String buildQualifiedRepository(
            String owner,
            String repository
    ) {
        if (owner.isBlank() || repository.isBlank()) {
            return "";
        }

        return owner + "/" + repository;
    }

    private String buildBranchName(
            String branchPrefix,
            Long incidentId,
            Long patchSuggestionId
    ) {
        if (branchPrefix.isBlank()) {
            return "";
        }

        return branchPrefix
                + "/inc-"
                + incidentId
                + "-patch-"
                + patchSuggestionId;
    }

    private String buildTitle(Incident incident) {
        String incidentTitle = normalizeSingleLine(incident.getTitle());

        String title = "[OpsLens] Fix incident #"
                + incident.getId()
                + ": "
                + incidentTitle;

        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }

        return title.substring(0, MAX_TITLE_LENGTH);
    }

    private String buildBody(
            Incident incident,
            PatchSuggestion patch,
            PatchVerificationResponse verification,
            String proposedBranch
    ) {
        StringBuilder body = new StringBuilder();

        body.append("## Incident\n\n")
                .append("- Incident: #")
                .append(patch.getIncidentId())
                .append("\n")
                .append("- Patch suggestion: #")
                .append(patch.getId())
                .append("\n")
                .append("- Project: ")
                .append(safeValue(incident.getProject()))
                .append("\n")
                .append("- Service: ")
                .append(safeValue(incident.getService()))
                .append("\n")
                .append("- Severity: ")
                .append(safeValue(incident.getSeverity()))
                .append("\n")
                .append("- Proposed branch: ")
                .append(safeValue(proposedBranch))
                .append("\n\n");

        body.append("## Root Cause\n\n")
                .append(safeValue(patch.getRootCause()))
                .append("\n\n");

        body.append("## Proposed Fix\n\n")
                .append(safeValue(patch.getPatchSummary()))
                .append("\n\n");

        body.append("## Validation\n\n")
                .append("- Patch validation: ")
                .append(Boolean.TRUE.equals(verification.getPatchValid())
                        ? "Passed"
                        : "Failed")
                .append("\n")
                .append("- Test run: ")
                .append(verification.getLatestTestRunId() == null
                        ? "Not available"
                        : "#" + verification.getLatestTestRunId())
                .append("\n")
                .append("- Test status: ")
                .append(safeValue(verification.getLatestTestStatus()))
                .append("\n\n");

        body.append("## Safety\n\n")
                .append("This pull request was generated by OpsLens ")
                .append("and requires human review before merging.\n");

        return body.toString();
    }

    private String normalizeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeSingleLine(String value) {
        if (value == null || value.isBlank()) {
            return "Untitled incident";
        }

        return value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeBranchPrefix(String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._/-]+", "-")
                .replaceAll("/{2,}", "/")
                .replaceAll("^[./-]+|[./-]+$", "");
    }

    private boolean isProtectedBranch(String branch) {
        return "main".equalsIgnoreCase(branch)
                || "master".equalsIgnoreCase(branch)
                || "production".equalsIgnoreCase(branch)
                || "release".equalsIgnoreCase(branch);
    }

    private String safeValue(String value) {
        return value == null || value.isBlank()
                ? "Not available"
                : value;
    }
}