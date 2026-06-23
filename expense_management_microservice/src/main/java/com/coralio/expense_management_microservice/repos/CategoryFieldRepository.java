package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.CategoryField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryFieldRepository extends JpaRepository<CategoryField, Long> {

    // ✅ Vérifier si un nom de champ existe déjà (utile pour la bibliothèque)
    boolean existsByFieldName(String fieldName);

    // ✅ Trouver un champ par son nom (unique)
    Optional<CategoryField> findByFieldName(String fieldName);

    // ❌ Toutes les méthodes qui utilisaient "category" ont été supprimées
    // Les opérations par catégorie sont désormais gérées via CategoryFieldMappingRepository
}