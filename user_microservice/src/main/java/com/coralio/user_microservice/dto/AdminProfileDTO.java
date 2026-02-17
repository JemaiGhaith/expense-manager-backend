package com.coralio.user_microservice.dto;

import lombok.Data;
import java.util.List;

@Data
public class AdminProfileDTO {
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
    private boolean emailVerified;
    private List<String> roles;
    private String lastLogin;
    private String createdAt;

    // Statistiques admin
    private int totalUsers;
    private int activeUsers;
    private int totalDepartments;
    private int totalProjects;
}