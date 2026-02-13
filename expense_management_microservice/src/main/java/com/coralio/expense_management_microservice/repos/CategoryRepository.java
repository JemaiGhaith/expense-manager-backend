package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.Category;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    // ✅ Ajouter cette méthode pour trouver les catégories actives
    List<Category> findByActiveTrue();

    // 🔥 FIX: Renamed method to avoid conflict with CrudRepository.deleteById
    @Modifying
    @Transactional
    @Query("DELETE FROM Category c WHERE c.id = :id")
    int deleteCategoryById(@Param("id") Long id);  // Renamed to deleteCategoryById
}
