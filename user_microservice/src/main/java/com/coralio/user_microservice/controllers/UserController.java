package com.coralio.user_microservice.controllers;

import com.coralio.user_microservice.config.KeycloakUserDto;
import com.coralio.user_microservice.services.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final KeycloakAdminClient keycloakClient;

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable("id") String id) {
        return ResponseEntity.ok(
                new UserDto(
                        keycloakClient.getUserById(id).getFirstName(),
                        keycloakClient.getUserById(id).getLastName(),
                        keycloakClient.getUserById(id).getUsername(),
                        keycloakClient.getUserById(id).getEmail()
                )
        );
    }
    /*
    public ResponseEntity<List<KeycloakUserDto>> getKeycloakUsers() {
        return ResponseEntity.ok(
                keycloakClient.getKeycloakUsers()
                        .stream()
                        .map(u -> new KeycloakUserDto(
                                u.getId(),
                                u.getUsername(),
                                u.getEmail(),
                                u.getFirstName(),
                                u.getLastName()
                        ))
                        .toList()
        );
    }
*/
    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(
                keycloakClient.getAllUsers()
                        .stream()
                        .map(user -> new UserDto(
                                user.getFirstName(),
                                user.getLastName(),
                                user.getUsername(),
                                user.getEmail()
                        ))
                        .collect(Collectors.toList())
        );
    }


    // DTO to send only necessary info
    public static class UserDto {
        public String firstName;
        public String lastName;
        public String username;
        public String email;

        public UserDto(String firstName, String lastName, String username, String email) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.username = username;
            this.email = email;
        }
    }
}