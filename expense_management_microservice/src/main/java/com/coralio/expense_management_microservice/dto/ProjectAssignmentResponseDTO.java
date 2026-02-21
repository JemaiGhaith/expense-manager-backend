package com.coralio.expense_management_microservice.dto;

import com.coralio.expense_management_microservice.enums.AssignmentStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProjectAssignmentResponseDTO {
    private Long id;
    private Long projectId;
    private String projectName;
    private String projectCode;
    private String userId;
    private String assignedBy;
    private LocalDateTime assignedAt;
    private AssignmentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}