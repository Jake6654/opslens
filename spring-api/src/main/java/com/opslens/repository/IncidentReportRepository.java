package com.opslens.repository;

import com.opslens.model.IncidentReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Allows the backend to fetch the report for a specific incident
public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {
    // The report may exist or not exist
    Optional<IncidentReport> findByIncidentId(Long incidentId);
}
