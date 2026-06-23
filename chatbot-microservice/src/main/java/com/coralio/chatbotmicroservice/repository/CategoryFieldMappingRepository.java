package com.coralio.chatbotmicroservice.repository;

import com.coralio.chatbotmicroservice.entity.CategoryFieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CategoryFieldMappingRepository extends JpaRepository<CategoryFieldMapping, Long> {

    // ✅ Fetch all mappings for a category, ordered by displayOrder
    List<CategoryFieldMapping> findByCategoryIdOrderByDisplayOrderAsc(Long categoryId);

    // ✅ Fetch only required mappings for a category (if needed)
    @Query("SELECT m FROM CategoryFieldMapping m WHERE m.category.id = :categoryId AND m.required = true ORDER BY m.displayOrder ASC")
    List<CategoryFieldMapping> findRequiredMappingsByCategoryId(@Param("categoryId") Long categoryId);

    // ✅ Check if a specific field is required for a category
    boolean existsByCategoryIdAndFieldIdAndRequiredTrue(Long categoryId, Long fieldId);
}