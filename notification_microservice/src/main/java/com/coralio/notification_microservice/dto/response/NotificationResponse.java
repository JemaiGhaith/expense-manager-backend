package com.coralio.notification_microservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private UUID userId;
    private String type;
    private String title;
    private String message;
    private Map<String, Object> data;
    private boolean read;
    private String priority;
    private String status;
    private LocalDateTime createdAt;
}