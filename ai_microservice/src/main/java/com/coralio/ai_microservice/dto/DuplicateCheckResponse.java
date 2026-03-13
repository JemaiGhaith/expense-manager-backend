package com.coralio.ai_microservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DuplicateCheckResponse {
    @JsonProperty("isDuplicate")
    private boolean isDuplicate;
    private double confidence;
    private String matchedFile;
    private String reason;
    private String detectionMethod;
    private String documentType;
}