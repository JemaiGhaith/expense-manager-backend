package com.coralio.chatbotmicroservice.repository;

import com.coralio.chatbotmicroservice.entity.CategoryField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryFieldRepository extends JpaRepository<CategoryField, Long> {

    // ✅ Unicity by fieldName remains valid (unique constraint on fieldName)
    Optional<CategoryField> findByFieldName(String fieldName);

    // ✅ List all distinct dynamic field names (useful for auto‑complete, etc.)
    @Query("SELECT DISTINCT cf.fieldName FROM CategoryField cf")
    List<String> findAllDynamicFieldNames();

    // ❌ REMOVED:
    // List<CategoryField> findByCategoryId(Long categoryId);
    // List<CategoryField> findByCategoryIdOrderByDisplayOrderAsc(Long categoryId);
    // @Query("SELECT cf FROM CategoryField cf WHERE cf.category.id = :categoryId AND cf.required = true")
    // List<CategoryField> findRequiredFieldsByCategoryId(@Param("categoryId") Long categoryId);
}