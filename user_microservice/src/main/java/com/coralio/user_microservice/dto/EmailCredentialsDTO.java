package com.coralio.user_microservice.dto;

import lombok.Data;

@Data
public class EmailCredentialsDTO {
    private String to;
    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private String departmentName;
    private String loginUrl = "http://localhost:8090/realms/coral-io_realm/protocol/openid-connect/auth?client_id=expense-app&redirect_uri=http%3A%2F%2Flocalhost%3A4200&state=4212b88e-9b6d-48ae-b7bc-0e0f83c8b16c&response_mode=fragment&response_type=code&scope=openid&nonce=d39ffeaf-0adc-4a6d-a881-c5776630ece2&code_challenge=UAoW9Fli4bGXOQmQTH_Z0o9UOzMN0zHyoIe2oXoFi7o&code_challenge_method=S256";
}