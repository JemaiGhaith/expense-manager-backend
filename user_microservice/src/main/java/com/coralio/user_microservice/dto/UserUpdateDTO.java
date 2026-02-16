package com.coralio.user_microservice.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class UserUpdateDTO {
    private String firstName;
    private String lastName;
    private String email;
    private String password;  // Optionnel
    private boolean enabled;
    private boolean emailVerified;
    private Map<String, List<String>> attributes;
    private List<String> realmRoles;
}