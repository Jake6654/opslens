package com.opslens.repository;

import com.opslens.model.TestFailureAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TestFailureAnalysisRepository
        extends JpaRepository<TestFailureAnalysis, Long> {

    Optional<TestFailureAnalysis> findByTestRunId(Long testRunId);
}