package com.coralio.user_microservice.config;

import lombok.Data;
import java.util.Map;

@Data
public class KeycloakUserDto {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String departmentId;      // ✅ AJOUTER
    private String departmentName;    // ✅ AJOUTER (optionnel)

    public KeycloakUserDto(String id,
                           String username,
                           String email,
                           String firstName,
                           String lastName,
                           String departmentId,    // ✅ AJOUTER
                           String departmentName) { // ✅ AJOUTER
        this.id = id;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.departmentId = departmentId;          // ✅ AJOUTER
        this.departmentName = departmentName;      // ✅ AJOUTER
    }
}