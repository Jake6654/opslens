package com.opslens.ai;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AiOrchestratorClient {


    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String orchestratorUrl;

    // @Value("${ai.orchestrator.url}") read a.orchestrator.url from application.yml
    public AiOrchestratorClient(
            @Value("${ai.orchestrator.url}") String orchestratorUrl
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.orchestratorUrl = orchestratorUrl;
    }

    public AnalyzeLogResponse analyzeLog(AnalyzeLogRequest request){
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("incident_id", request.getIncidentId());
        payload.put("log_id", request.getLogId());
        payload.put("project", request.getProject());
        payload.put("environment", request.getEnvironment());
        payload.put("service", request.getService());
        payload.put("severity", request.getSeverity());
        payload.put("message", request.getMessage());
        String jsonBody = toJson(payload);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(orchestratorUrl + "/analyze-log"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "AI orchestrator returned " + response.statusCode() + ": " + response.body()
                );
            }

            return objectMapper.readValue(response.body(), AnalyzeLogResponse.class);
        } catch (IOException error) {
            throw new IllegalStateException("AI orchestrator request failed", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI orchestrator request was interrupted", error);
        }
    }

    public CodeSearchResponse searchCode(CodeSearchRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("incident_id", request.getIncidentId());
        payload.put("project", request.getProject());
        payload.put("environment", request.getEnvironment());
        payload.put("service", request.getService());
        payload.put("severity", request.getSeverity());
        payload.put("message", request.getMessage());
        payload.put("analysis_summary", request.getAnalysisSummary());
        payload.put("suspected_root_cause", request.getSuspectedRootCause());

        String jsonBody = toJson(payload);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(orchestratorUrl + "/search-code"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "AI orchestrator returned " + response.statusCode() + ": " + response.body()
                );
            }

            return objectMapper.readValue(response.body(), CodeSearchResponse.class);
        } catch (IOException error) {
            throw new IllegalStateException("Code search request failed", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Code search request was interrupted", error);
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Could not serialize AI analysis request", error);
        }
    }

    public PatchSuggestionResponse suggestPatch(PatchSuggestionRequest request){
        // Build JSON Payload: convert Java object to JSON structure that FastAPI expects
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("incident_id", request.getIncidentId());
        payload.put("summary", request.getSummary());
        payload.put("suspected_root_cause", request.getSuspectedRootCause());
        payload.put("recommended_action", request.getRecommendedAction());
        payload.put("code_results", request.getCodeResults());

        String jsonBody = toJson(payload);

        // Build HTTP Request that spring boot is going to send it to FastAPI
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(orchestratorUrl + "/suggest-patch"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try{
            // Send HTTP request
            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            // Check HTTP Status
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "AI orchestrator returned " + response.statusCode() + ": " + response.body()
                );
            }

            // Parse JSON response to Java DTO
            return  objectMapper.readValue(response.body(), PatchSuggestionResponse.class);
        } catch (IOException error) {
            throw new IllegalStateException("Patch suggestion request failed", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Patch suggestion request was interrupted", error);
        }

    }

    public RunTestsResponse runTests(RunTestsRequest request) {
        String jsonBody;

        try {
            jsonBody = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to serialize run tests request", error);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(orchestratorUrl + "/run-tests"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "AI orchestrator returned " + response.statusCode()
                );
            }

            return objectMapper.readValue(response.body(), RunTestsResponse.class);
        } catch (IOException error) {
            throw new IllegalStateException("Run tests request failed", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Run tests request was interrupted", error);
        }
    }


}
