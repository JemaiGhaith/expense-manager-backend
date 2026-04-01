package com.coralio.notification_microservice.repository;

import com.coralio.notification_microservice.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {
    Optional<UserPreference> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
}