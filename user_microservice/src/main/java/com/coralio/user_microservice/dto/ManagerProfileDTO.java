package com.coralio.user_microservice.dto;

import lombok.Data;
import java.util.List;

@Data
public class ManagerProfileDTO {
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
    private List<String> roles;

    // Statistiques manager
    private int pendingExpenses;
    private int totalTeamMembers;
}