package com.coralio.expense_management_microservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())                  // Désactive CSRF (API REST)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()   // Health check public
                        .requestMatchers("/api/projects/**").permitAll()
                        .anyRequest().authenticated()                     // Tout le reste nécessite un token
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}