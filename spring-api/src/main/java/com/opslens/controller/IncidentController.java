package com.opslens.controller;


import com.opslens.model.Incident;
import com.opslens.model.IncidentReport;
import com.opslens.model.PatchSuggestion;
import com.opslens.service.CodeSearchService;
import com.opslens.service.IncidentService;
import com.opslens.service.PatchSuggestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController // This class handles HTTP requests and returns JSON responses
@RequestMapping("/incidents")
public class IncidentController {
    private final IncidentService incidentService;
    private final CodeSearchService codeSearchService;
    private final PatchSuggestionService patchSuggestionService;

    public IncidentController(IncidentService incidentService, CodeSearchService codeSearchService, PatchSuggestionService patchSuggestionService)
    {
        this.incidentService = incidentService;
        this.codeSearchService = codeSearchService;
        this.patchSuggestionService = patchSuggestionService;
    }

    @PostMapping("/from-log/{logId}")
    // Read logId from the URL path
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

    @PostMapping("/{id}/code-search")
    public ResponseEntity<?> runCodeSearch(@PathVariable Long id){
        try {
            return ResponseEntity.ok(codeSearchService.searchCodeForIncident(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/code-search")
    public ResponseEntity<?> getCodeSearchResults(@PathVariable Long id) {
        return ResponseEntity.ok(codeSearchService.getCodeSearchResults(id));
    }

    // Generate and save new patch suggestion
    @PostMapping("/{id}/suggest-patch")
    public ResponseEntity<?> suggestPatch(@PathVariable Long id){
        try{
            PatchSuggestion suggestion = patchSuggestionService.suggestPatchForIncident(id);
            return ResponseEntity.ok(suggestion);
        } catch (IllegalArgumentException error) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        }
    }

    // read saved patch suggestions
    @GetMapping("/{id}/patch-suggestions")
    public ResponseEntity<List<PatchSuggestion>> getPatchSuggestions(@PathVariable Long id) {
        return ResponseEntity.ok(patchSuggestionService.getPatchSuggestions(id));
    }
}
