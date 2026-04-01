package com.coralio.ai_microservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DuplicateResult {

    @JsonProperty("duplicate")
    private boolean duplicate;

    @JsonProperty("matchedFile")
    private String matchedFile;

    @JsonProperty("matchedPath")
    private String matchedPath;

    @JsonProperty("score")
    private double score;

    public DuplicateResult() {}

    // ✅ Constructeur avec matchedPath
    public DuplicateResult(boolean duplicate, String matchedFile, String matchedPath, double score) {
        this.duplicate = duplicate;
        this.matchedFile = matchedFile;
        this.matchedPath = matchedPath;
        this.score = score;
    }

    public boolean isDuplicate() { return duplicate; }
    public String getMatchedFile() { return matchedFile; }
    public String getMatchedPath() { return matchedPath; }
    public double getScore() { return score; }
}