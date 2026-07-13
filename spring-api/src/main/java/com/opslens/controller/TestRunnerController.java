package com.opslens.controller;

import com.opslens.model.TestRunResult;
import com.opslens.service.TestRunnerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TestRunnerController {

    private final TestRunnerService testRunnerService;

    public TestRunnerController(TestRunnerService testRunnerService) {
        this.testRunnerService = testRunnerService;
    }

    @PostMapping("/patch-suggestions/{id}/run-tests")
    public ResponseEntity<TestRunResult> runTests(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(testRunnerService.runTestsForPatchSuggestion(id));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/patch-suggestions/{id}/test-runs")
    public List<TestRunResult> getTestRuns(@PathVariable Long id) {
        return testRunnerService.getTestRunsForPatchSuggestion(id);
    }
}