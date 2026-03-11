package com.coralio.ai_microservice.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
@JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class DuplicateCheckResponse {

    // ✅ Forcer le nom JSON
    @JsonProperty("isDuplicate")
    private boolean isDuplicate;

    private double confidence;
    private String matchedFile;
    private String reason;
    private String detectionMethod;

    // ✅ Nouveau champ
    private String documentType;
}