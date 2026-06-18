package com.opslens.controller;


import com.opslens.model.Incident;
import com.opslens.model.IncidentReport;
import com.opslens.service.IncidentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController // This class handles HTTP requests and returns JSON responses
@RequestMapping("/incidents")
public class IncidentController {
    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping("/from-log/{logId}")
    public ResponseEntity<?> createdIncidentFromLog(@PathVariable Long logId) {
        try {
            Incident incident = incidentService.createIncidentFromLog(logId);
            return ResponseEntity.ok(incident);
        } catch (IllegalArgumentException error) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public List<Incident> getIncidents(){
        return incidentService.getIncidents();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Incident> getIncidentById(@PathVariable Long id){
        return incidentService.getIncidentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<IncidentReport> getIncidentReport(@PathVariable Long id) {
        return incidentService.getReportByIncidentId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
