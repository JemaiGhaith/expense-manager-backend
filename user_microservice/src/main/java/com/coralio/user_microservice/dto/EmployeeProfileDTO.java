package com.coralio.user_microservice.dto;

import lombok.Data;

@Data
public class EmployeeProfileDTO {
    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String location;
    private String departmentId;
    private String departmentName;
    private boolean enabled;

    // Statistiques employé
    private int pendingExpenses;
    private int totalExpenses;
}