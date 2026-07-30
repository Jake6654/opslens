package com.opslens.controller;


import com.opslens.dto.PullRequestPreflightResponse;
import com.opslens.service.PullRequestPreflightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/patch-suggestions")
public class PullRequestController {

    private final PullRequestPreflightService preflightService;
    private final PullRequestPreflightService pullRequestPreflightService;

    public PullRequestController(PullRequestPreflightService preflightService, PullRequestPreflightService pullRequestPreflightService) {
        this.preflightService = preflightService;
        this.pullRequestPreflightService = pullRequestPreflightService;
    }

    @GetMapping("/{id}/pull-request/preflight")
    public ResponseEntity<PullRequestPreflightResponse> preflight(
            @PathVariable Long id
    ) {
        try{
            PullRequestPreflightResponse response = preflightService.buildPlan(id);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException error){
            return ResponseEntity.notFound().build();
        }
    }
}
