package com.opslens.repository;

import com.opslens.model.LogItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// This repository manages LogItem entities, and their ID type is Long
public interface LogRepository extends JpaRepository<LogItem, Long> {

    // This custom method finds all logs where the level field matches the given value
    // ignoring uppercase and lowercase
    List<LogItem> findByLevelIgnoreCase(String level);

    List<LogItem> findByProject(String project);
    List<LogItem> findByProjectAndEnvironment(String project, String environment);
}


