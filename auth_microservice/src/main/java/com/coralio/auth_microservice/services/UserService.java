package com.coralio.auth_microservice.services;
import com.coralio.auth_microservice.entities.User;
import com.coralio.auth_microservice.repos.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Créer un utilisateur
    public User createUser(User user) {

        // Valeurs forcées côté backend
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }
    // Récupérer un utilisateur par ID
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    // Récupérer tous les utilisateurs
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // Mettre à jour un utilisateur
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    // Supprimer un utilisateur
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    // Recherche par username
    public Optional<User> getByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    // Recherche par email
    public Optional<User> getByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
