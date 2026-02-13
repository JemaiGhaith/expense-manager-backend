package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.Project;
import com.coralio.expense_management_microservice.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Project> findByStatus(ProjectStatus status);

    List<Project> findByDepartmentId(Long departmentId);

    @Query("SELECT p FROM Project p WHERE " +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Project> searchProjects(@Param("search") String searchTerm);
}
