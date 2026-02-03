package com.coralio.user_microservice.config;

import lombok.Data;

@Data
public class KeycloakUserDto {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;

    public KeycloakUserDto(String id,
                           String username,
                           String email,
                           String firstName,
                           String lastName) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }
}
