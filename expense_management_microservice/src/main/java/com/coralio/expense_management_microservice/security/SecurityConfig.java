package com.coralio.expense_management_microservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {



    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // désactive CSRF
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/expenses/**").permitAll() // endpoints publics
                        .requestMatchers("/api/categories/**").permitAll() // endpoints publics
                        .requestMatchers("/api/projects/**").permitAll() // endpoints publics

                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));


        return http.build();
    }

}
