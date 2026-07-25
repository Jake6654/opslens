package com.opslens.controller;

import com.opslens.model.TestFailureAnalysis;
import com.opslens.service.TestFailureAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestFailureAnalysisController {

    private final TestFailureAnalysisService service;

    public TestFailureAnalysisController(TestFailureAnalysisService service) {
        this.service = service;
    }

    @GetMapping("/test-runs/{testRunId}/analysis")
    public ResponseEntity<TestFailureAnalysis> getAnalysis(
            @PathVariable Long testRunId
    ) {
        return service.getAnalysisByTestRunId(testRunId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
