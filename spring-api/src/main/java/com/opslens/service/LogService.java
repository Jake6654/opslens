package com.opslens.service;


import com.opslens.model.LogItem;
import com.opslens.model.LogSummary;
import com.opslens.repository.LogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LogService {

    private final LogRepository logRepository;

    // LogService needs LogRepository, and Spring injects it automatically
    // Since LogService is marked with @Service
    public LogService(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    // filtering many logs
    public List<LogItem> getLogs(String level, String project, String environment){
        List<LogItem> logs = logRepository.findAll();

        if (level != null && !level.isBlank()) {
            logs = logs.stream()
                    .filter(log -> log.getLevel() != null)
                    .filter(log -> log.getLevel().equalsIgnoreCase(level))
                    .toList();
        }
        // This condition checks the request parameter
        // Did the user provide a project filter in the URL?
        if (project != null && !project.isBlank())  {
            logs = logs.stream()
                    // This part is different
                    // This checks the actual log data
                    // Does this specific log object have a project value?
                    .filter(log -> log.getProject() != null)
                    .filter(log -> log.getProject().equalsIgnoreCase(project))
                    .toList();
        }


        if (environment != null && !environment.isBlank()){
            logs = logs.stream()
                    .filter(log -> log.getEnvironment() != null)
                    .filter(log -> log.getEnvironment().equalsIgnoreCase(environment))
                    .toList();
        }

        return logs;
    }


    public LogSummary getLogSummary(){
        // instead of duplicating the log list, you call existing method and get all logs
        List<LogItem> logs = getLogs(null, null, null);

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

    // fetching exactly one log
    // Optional<LogItem> means Maybe the log exists or does not exist
    public Optional<LogItem> getLogByID(Long id) {
        return logRepository.findById(id);
    }
}
