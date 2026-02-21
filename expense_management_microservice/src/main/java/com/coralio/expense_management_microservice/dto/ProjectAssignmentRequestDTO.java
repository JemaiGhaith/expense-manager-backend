package com.coralio.expense_management_microservice.dto;

import com.coralio.expense_management_microservice.enums.AssignmentStatus;
import lombok.Data;

@Data
public class ProjectAssignmentRequestDTO {
    private Long projectId;
    private String userId;
    private AssignmentStatus status;
}