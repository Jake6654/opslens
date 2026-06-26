package com.opslens.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CodeSearchItem {

    private String repository;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("symbol_name")
    private String symbolName;

    private String snippet;

    @JsonProperty("relevance_reason")
    private String relevanceReason;

    private Double score;

    public CodeSearchItem() {
    }

    public String getRepository() {
        return repository;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getSymbolName() {
        return symbolName;
    }

    public String getSnippet() {
        return snippet;
    }

    public String getRelevanceReason() {
        return relevanceReason;
    }

    public Double getScore() {
        return score;
    }
}
