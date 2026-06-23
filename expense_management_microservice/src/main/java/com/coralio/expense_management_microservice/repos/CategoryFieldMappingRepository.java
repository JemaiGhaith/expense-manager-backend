package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.CategoryFieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryFieldMappingRepository extends JpaRepository<CategoryFieldMapping, Long> {
    List<CategoryFieldMapping> findByCategoryId(Long categoryId);
    void deleteByCategoryId(Long categoryId);
}