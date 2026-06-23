// expense-microservice/src/main/java/com/coralio/expense_management_microservice/dto/NotificationRequestDTO.java
package com.coralio.expense_management_microservice.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class NotificationRequestDTO {
    private UUID userId;
    private String userEmail;
    private String type;
    private String title;
    private String message;
    private Map<String, Object> data;
    private String priority;
    private String sourceService;
    private String sourceEntityId;
    private String sourceEntityType;
    private Boolean sendEmail;
}