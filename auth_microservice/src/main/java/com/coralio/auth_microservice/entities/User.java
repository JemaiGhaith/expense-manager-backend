package com.coralio.auth_microservice.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // getters & setters

    // 👇 Getter correct pour boolean
    public boolean isActive() {
        return active;
    }

    // Setter
    public void setActive(boolean active) {
        this.active = active;
    }
}
