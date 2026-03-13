package com.coralio.ai_microservice.model;


public class DuplicateResult {

    private boolean duplicate;
    private String matchedFile;
    private double score;

    public DuplicateResult(){}

    public DuplicateResult(boolean duplicate,String matchedFile,double score){
        this.duplicate=duplicate;
        this.matchedFile=matchedFile;
        this.score=score;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public String getMatchedFile() {
        return matchedFile;
    }

    public double getScore() {
        return score;
    }

}