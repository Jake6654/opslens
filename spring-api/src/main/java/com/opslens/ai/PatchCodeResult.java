package com.opslens.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

// It is converted from the database entity: CodeSearchResult
// FastAPI expects this shape:
// {
//  "repository": "local-workspace",
//  "file_path": "backend/src/main/java/DiaryService.java",
//  "snippet": "...",
//  "relevance_reason": "Matched local search query: DiaryService",
//  "score": 0.75
//}
public class PatchCodeResult {

    private String repository;

    @JsonProperty("file_path")
    private String filePath;

    private String snippet;

    @JsonProperty("relevance_reason")
    private String relevanceReason;

    private Double score;


    public PatchCodeResult() {

    }

    public PatchCodeResult(String repository, String filePath, String relevanceReason, String snippet, Double score) {
        this.repository = repository;
        this.filePath = filePath;
        this.snippet = snippet;
        this.relevanceReason = relevanceReason;
        this.score = score;
    }

    public String getRepository() {
        return repository;
    }
    @JsonProperty("file_path") // this maps Java filepath to Json file_path
    public String getFilePath() {
        return filePath;
    }

    public String getSnippet() {
        return snippet;
    }

    @JsonProperty("relevance_reason")
    public String getRelevanceReason() {
        return relevanceReason;
    }

    public Double getScore() {
        return score;
    }
}
