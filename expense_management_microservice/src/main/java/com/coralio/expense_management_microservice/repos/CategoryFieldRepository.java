package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.CategoryField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CategoryFieldRepository extends JpaRepository<CategoryField, Long> {

    // ✅ Pour récupérer les champs d'une catégorie (triés par ordre)
    List<CategoryField> findByCategoryIdOrderByDisplayOrderAsc(Long categoryId);

    // ✅ Pour supprimer tous les champs d'une catégorie (lors de modification)
    void deleteByCategoryId(Long categoryId);
}