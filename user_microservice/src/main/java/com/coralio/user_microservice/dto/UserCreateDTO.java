package com.coralio.user_microservice.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class UserCreateDTO {
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private boolean enabled = true;
    private boolean emailVerified = false;
    private Map<String, List<String>> attributes;
    private List<String> realmRoles;
}