package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.Department;
import com.coralio.expense_management_microservice.enums.DepartmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByCode(String code);

    Optional<Department> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<Department> findByStatus(DepartmentStatus status);

    @Query("SELECT d FROM Department d WHERE " +
            "LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(d.code) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(d.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(d.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(d.location) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Department> searchDepartments(@Param("search") String searchTerm);
}