package com.opslens.repository;

import com.opslens.model.PatchSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatchSuggestionRepository extends JpaRepository<PatchSuggestion, Long> {
    List<PatchSuggestion> findByIncidentId(Long incidentId);
}
