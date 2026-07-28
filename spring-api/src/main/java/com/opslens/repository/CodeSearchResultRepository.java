package com.opslens.repository;

import com.opslens.model.CodeSearchResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeSearchResultRepository extends JpaRepository<CodeSearchResult, Long> {
    List<CodeSearchResult> findByIncidentId(Long incidentId);

    List<CodeSearchResult> findByIncidentIdOrderByScoreDescCreatedAtDesc(Long incidentId);

    void deleteByIncidentId(Long incidentId);
}
