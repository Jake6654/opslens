package com.opslens.controller;

import com.opslens.dto.CreatePullRequestBranchResponse;
import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.github.GitHubApiException;
import com.opslens.service.PullRequestBlockedException;
import com.opslens.service.PullRequestBranchConflictException;
import com.opslens.service.PullRequestBranchService;
import com.opslens.service.PullRequestPreflightService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/patch-suggestions")
public class PullRequestController {

    private final PullRequestPreflightService preflightService;
    private final PullRequestBranchService branchService;

    public PullRequestController(
            PullRequestPreflightService preflightService,
            PullRequestBranchService branchService
    ) {
        this.preflightService = preflightService;
        this.branchService = branchService;
    }

    @GetMapping("/{id}/pull-request/preflight")
    public ResponseEntity<PullRequestPreflightResponse> preflight(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(preflightService.buildPlan(id));
    }

    @PostMapping("/{id}/pull-request/branch")
    public ResponseEntity<CreatePullRequestBranchResponse> createBranch(
            @PathVariable Long id
    ) {
        CreatePullRequestBranchResponse response =
                branchService.createBranch(id);

        if (response.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            IllegalArgumentException error
    ) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                error.getMessage(),
                null
        );
    }

    @ExceptionHandler(PullRequestBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleBlocked(
            PullRequestBlockedException error
    ) {
        return errorResponse(
                HttpStatus.CONFLICT,
                error.getMessage(),
                error.getBlockers()
        );
    }

    @ExceptionHandler(PullRequestBranchConflictException.class)
    public ResponseEntity<Map<String, Object>> handleBranchConflict(
            PullRequestBranchConflictException error
    ) {
        return errorResponse(
                HttpStatus.CONFLICT,
                error.getMessage(),
                null
        );
    }

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<Map<String, Object>> handleGitHubError(
            GitHubApiException error
    ) {
        return errorResponse(
                HttpStatus.BAD_GATEWAY,
                error.getMessage(),
                null
        );
    }

    private ResponseEntity<Map<String, Object>> errorResponse(
            HttpStatus status,
            String message,
            Object blockers
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);

        if (blockers != null) {
            body.put("blockers", blockers);
        }

        return ResponseEntity.status(status).body(body);
    }
}