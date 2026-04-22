package com.opslens.service;


import com.opslens.model.LogItem;
import com.opslens.model.LogSummary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogService {

    public List<LogItem> getLogs(String level){

        // Get all logs
        List<LogItem> logs = List.of(
                new LogItem("2026-04-18T13:00:00", "INFO", "spring-api", "User login success"),
                new LogItem("2026-04-18T13:01:10", "WARN", "spring-api", "Slow response detected"),
                new LogItem("2026-04-18T13:02:25", "ERROR", "spring-api", "Database timeout")
        );

        if (level == null){
            return logs;
        }

        // filtering logic
        return logs.stream()
                // filter () expects a function that returns true of false
                // if log.getlevel is equal to level then do toList
                .filter(log -> log.getLevel().equalsIgnoreCase(level))
                .toList();

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
}
