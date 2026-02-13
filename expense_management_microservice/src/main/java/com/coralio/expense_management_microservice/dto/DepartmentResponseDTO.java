package com.coralio.expense_management_microservice.dto;

import com.coralio.expense_management_microservice.enums.DepartmentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DepartmentResponseDTO {
    private Long id;
    private String name;
    private String code;
    private DepartmentStatus status;
    private String description;
    private String email;
    private String phone;
    private String location;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime updatedAt;
}