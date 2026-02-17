package com.coralio.user_microservice.dto;

import lombok.Data;

@Data
public class ProfileUpdateDTO {
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String location;
}