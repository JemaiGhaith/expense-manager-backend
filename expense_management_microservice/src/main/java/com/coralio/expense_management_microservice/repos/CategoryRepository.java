package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    // ✅ Ajouter cette méthode pour trouver les catégories actives
    List<Category> findByActiveTrue();

    // ✅ Garder la méthode existante pour trouver par nom
    Optional<Category> findByName(String name);
}
