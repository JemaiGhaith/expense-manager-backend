package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.entities.EmployeeProjectAssignment;
import com.coralio.expense_management_microservice.entities.Project;
import com.coralio.expense_management_microservice.repos.EmployeeProjectAssignmentRepository;
import com.coralio.expense_management_microservice.repos.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmployeeProjectService {

    private final EmployeeProjectAssignmentRepository assignmentRepository;
    private final ProjectRepository projectRepository;

    public EmployeeProjectService(EmployeeProjectAssignmentRepository assignmentRepository,
                                  ProjectRepository projectRepository) {
        this.assignmentRepository = assignmentRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public void assignEmployeeToProject(String employeeId, Long projectId) {
        if (assignmentRepository.existsByEmployeeIdAndProjectId(employeeId, projectId)) {
            throw new RuntimeException("L'employé est déjà affecté à ce projet");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Projet non trouvé"));

        EmployeeProjectAssignment assignment = EmployeeProjectAssignment.builder()
                .employeeId(employeeId)
                .project(project)
                .assignedAt(LocalDateTime.now())
                .build();
        assignmentRepository.save(assignment);
    }

    @Transactional
    public void unassignEmployeeFromProject(String employeeId, Long projectId) {
        assignmentRepository.deleteByEmployeeIdAndProjectId(employeeId, projectId);
    }

    public List<Project> getProjectsForEmployee(String employeeId) {
        return assignmentRepository.findByEmployeeId(employeeId)
                .stream()
                .map(EmployeeProjectAssignment::getProject)
                .collect(Collectors.toList());
    }

    public List<String> getEmployeesForProject(Long projectId) {
        return assignmentRepository.findByProjectId(projectId)
                .stream()
                .map(EmployeeProjectAssignment::getEmployeeId)
                .collect(Collectors.toList());
    }
}