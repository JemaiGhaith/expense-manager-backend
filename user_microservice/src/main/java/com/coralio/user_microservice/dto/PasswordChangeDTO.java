package com.coralio.user_microservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordChangeDTO {
    private String currentPassword;
    private String newPassword;
    private String confirmPassword;

    // Validation optionnelle
    public boolean isValid() {
        return newPassword != null && newPassword.length() >= 6
                && newPassword.equals(confirmPassword);
    }
}