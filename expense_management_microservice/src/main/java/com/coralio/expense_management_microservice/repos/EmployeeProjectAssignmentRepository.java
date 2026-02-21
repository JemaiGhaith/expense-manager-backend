package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.EmployeeProjectAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EmployeeProjectAssignmentRepository extends JpaRepository<EmployeeProjectAssignment, Long> {
    List<EmployeeProjectAssignment> findByEmployeeId(String employeeId);
    List<EmployeeProjectAssignment> findByProjectId(Long projectId);
    boolean existsByEmployeeIdAndProjectId(String employeeId, Long projectId);
    void deleteByEmployeeIdAndProjectId(String employeeId, Long projectId);
    void deleteByProjectId(Long projectId);
}