package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.CategoryField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface CategoryFieldRepository extends JpaRepository<CategoryField, Long> {

    // ✅ Pour récupérer les champs d'une catégorie (triés par ordre)
    List<CategoryField> findByCategoryIdOrderByDisplayOrderAsc(Long categoryId);

    // ✅ Méthode pour supprimer par catégorie
    @Modifying
    @Transactional
    @Query("DELETE FROM CategoryField cf WHERE cf.category.id = :categoryId")
    int deleteByCategoryId(@Param("categoryId") Long categoryId);

    // ✅ Méthode pour trouver par catégorie
    List<CategoryField> findByCategoryId(Long categoryId);

    @Modifying
    @Transactional
    @Query("DELETE FROM CategoryField cf WHERE cf.id IN :ids")
    int deleteAllByIdInBatch(@Param("ids") List<Long> ids);
}