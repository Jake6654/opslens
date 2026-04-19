package com.opslens.service;


import com.opslens.model.LogItem;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogService {

    public List<LogItem> getLogs(){
        return List.of(
                // return multiple log items grouped by a list
                // Spring automatically converts it to JSON
                new LogItem("2026-04-18T13:00:00", "INFO", "spring-api", "User login success"),
                new LogItem("2026-04-18T13:01:10", "WARN", "spring-api", "Slow response detected"),
                new LogItem("2026-04-18T13:02:25", "ERROR", "spring-api", "Database timeout")
        );
    }
}
