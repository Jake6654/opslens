package com.opslens.controller;


import com.opslens.model.LogItem;
import com.opslens.service.LogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
public class HealthController {

    // Construction injection
    // means the controller needs a LogService, and Spring provides it
    private final LogService logService;

    public HealthController(LogService logService) {
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
}
