package com.opslens.repository;

import com.opslens.model.TestRunResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TestRunResultRepository extends JpaRepository<TestRunResult, Long> {
    List<TestRunResult> findByPatchSuggestionId(Long patchSuggestionId);

    Optional<TestRunResult> findTopByPatchSuggestionIdOrderByCreatedAtDescIdDesc(
            Long patchSuggestionId
    );
}
