package com.coralio.expense_management_microservice.dto;

import com.coralio.expense_management_microservice.enums.ProjectStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProjectRequestDTO {
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
}