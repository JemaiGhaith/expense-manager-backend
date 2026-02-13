package com.coralio.expense_management_microservice.dto;

import com.coralio.expense_management_microservice.enums.DepartmentStatus;
import lombok.Data;

@Data
public class DepartmentRequestDTO {
    private String name;
    private String code;
    private DepartmentStatus status;
    private String description;
    private String email;
    private String phone;
    private String location;
}