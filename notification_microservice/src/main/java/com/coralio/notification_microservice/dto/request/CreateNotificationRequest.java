package com.coralio.notification_microservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class CreateNotificationRequest {
    @NotNull
    private UUID userId;

    @NotBlank
    private String userEmail;

    @NotBlank
    private String type;

    @NotBlank
    private String title;

    @NotBlank
    private String message;

    private Map<String, Object> data;

    private String priority;

    private String sourceService;
    private UUID sourceEntityId;
    private String sourceEntityType;

    private Integer expiresInHours;
    private Boolean sendEmail;
}
