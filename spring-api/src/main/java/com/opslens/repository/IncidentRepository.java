package com.opslens.repository;

import com.opslens.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// This repository manages Incident entities whose id type is Long
public interface IncidentRepository extends JpaRepository<Incident, Long> {
    // These two are Spring Data JPA derived queries
    // Spring reads the method name and creates the SQL automatically
    List<Incident> findByProjectIgnoreCase(String project);
    List<Incident> findByStatusIgnoreCase(String status);

    Optional<Incident> findBySourceLogId(Long sourceLogId);
    
    
}

