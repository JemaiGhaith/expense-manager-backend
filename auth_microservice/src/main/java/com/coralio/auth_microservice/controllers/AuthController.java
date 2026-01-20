package com.coralio.auth_microservice.controllers;

import com.coralio.auth_microservice.entities.User;
import com.coralio.auth_microservice.repos.UserRepository;
import com.coralio.auth_microservice.security.JwtUtils;
import com.coralio.auth_microservice.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authManager;
    private final JwtUtils jwtUtils;
    private final BCryptPasswordEncoder passwordEncoder;
    @Autowired
    private UserRepository  userRepository;
    public AuthController(UserService userService, AuthenticationManager authManager,
                          JwtUtils jwtUtils, BCryptPasswordEncoder encoder) {
        this.userService = userService;
        this.authManager = authManager;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = encoder;
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return ResponseEntity.ok(userService.createUser(user));
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody User user) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword())
            );
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        // Récupérer l'utilisateur complet depuis la BDD
        User dbUser = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtUtils.generateToken(dbUser); // <-- passe le User complet
        return ResponseEntity.ok(token);
    }

}
