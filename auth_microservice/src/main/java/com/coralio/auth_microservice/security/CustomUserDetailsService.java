package com.coralio.auth_microservice.security;

import com.coralio.auth_microservice.entities.User;
import com.coralio.auth_microservice.repos.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository repo) {
        this.userRepository = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Spring Security attend que le rôle commence par "ROLE_"
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name()) // attention, il ajoute "ROLE_" automatiquement
                .disabled(!user.isActive())
                .build();
    }
}
