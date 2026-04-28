package com.opslens.controller;


import com.opslens.model.LogItem;
import com.opslens.model.LogSummary;
import com.opslens.service.LogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
public class LogController {

    // Construction injection
    // means the controller needs a LogService, and Spring provides it
    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping("/health")
    public String health(){
        return "OK";
    }

    @GetMapping("/logs")
    public List<LogItem> getLogs(
            // Read level from URL query parameter
            @RequestParam(required = false) String level
    ){
        return logService.getLogs(level);
    }

    @GetMapping("/logs/summary")
    public LogSummary getLogSummary() {
        return logService.getLogSummary();
    }

    @PostMapping("/logs")
    public ResponseEntity<?> createLog(
            @RequestHeader("x-api-key") String apiKey,
            @RequestBody LogItem logItem
    ){
        String expectedApiKey = System.getenv("API_KEY");

        if (expectedApiKey == null || !expectedApiKey.equals(apiKey)) {
            return ResponseEntity.status(401).body("Invalid API key");
        }
        return ResponseEntity.ok(logService.saveLog(logItem));
    }


}
