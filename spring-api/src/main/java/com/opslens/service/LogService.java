package com.opslens.service;


import com.opslens.model.LogItem;
import com.opslens.model.LogSummary;
import com.opslens.repository.LogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogService {

    private final LogRepository logRepository;

    // LogService needs LogRepository, and Spring injects it automatically
    // Since LogService is marked with @Service
    public LogService(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public List<LogItem> getLogs(String level){
        if (level == null || level.isBlank()){
            // Now data comes from the database
            // if there's no level filter, returns all logs
            return logRepository.findAll();
        }

        return logRepository.findByLevelIgnoreCase(level);
    }


    public LogSummary getLogSummary(){
        // instead of duplicating the log list, you call existing method and get all logs
        List<LogItem> logs = getLogs(null);

        long errorCount = logs.stream()
                .filter(log -> log.getLevel().equalsIgnoreCase("ERROR"))
                .count();

        long warnCount = logs.stream()
                .filter(log -> log.getLevel().equalsIgnoreCase("WARN"))
                .count();

        long infoCount = logs.stream()
                .filter(log -> log.getLevel().equalsIgnoreCase("INFO"))
                .count();

        return new LogSummary(logs.size(), errorCount, warnCount, infoCount);
    }


    public LogItem saveLog(LogItem logItem){
        return logRepository.save(logItem);
    }
}
