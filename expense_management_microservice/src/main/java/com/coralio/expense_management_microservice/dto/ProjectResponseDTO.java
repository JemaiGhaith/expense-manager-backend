package com.coralio.expense_management_microservice.dto;

import com.coralio.expense_management_microservice.enums.ProjectStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectResponseDTO {
    private Long id;
    private String name;
    private String code;
    private Long departmentId;
    private ProjectStatus status;
    private String description;
    private Double budget;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime updatedAt;
}