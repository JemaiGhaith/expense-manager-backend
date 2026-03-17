package com.coralio.chatbotmicroservice.repository;

import com.coralio.chatbotmicroservice.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByActiveTrue();

    Optional<Category> findByName(String name);

    @Query("SELECT c FROM Category c WHERE c.active = true AND c.plafond > :minPlafond")
    List<Category> findActiveWithMinPlafond(@Param("minPlafond") Double minPlafond);

    @Query("SELECT AVG(c.plafond) FROM Category c WHERE c.active = true")
    Double getAveragePlafond();

    @Query("SELECT MAX(c.plafond) FROM Category c WHERE c.active = true")
    Double getMaxPlafond();

    @Query("SELECT c FROM Category c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Category> searchByName(@Param("search") String search);

    @Query("SELECT COUNT(c) FROM Category c WHERE c.active = true")
    long countActive();
}