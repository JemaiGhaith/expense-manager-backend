package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.dto.ProjectResponseDTO;
import com.coralio.expense_management_microservice.entities.EmployeeProjectAssignment;
import com.coralio.expense_management_microservice.entities.Project;
import com.coralio.expense_management_microservice.enums.ProjectStatus;
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

    /**
     * Récupère les IDs des employés affectés à un projet
     */
    public List<String> getProjectEmployees(Long projectId) {
        return assignmentRepository.findEmployeeIdsByProjectId(projectId);
    }

    /**
     * Récupère les projets actifs d'un employé
     */
    public List<ProjectResponseDTO> getEmployeeProjects(String employeeId) {
        List<EmployeeProjectAssignment> assignments =
                assignmentRepository.findByEmployeeId(employeeId);

        return assignments.stream()
                .map(EmployeeProjectAssignment::getProject)
                .filter(project -> project.getStatus() == ProjectStatus.ACTIF)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convertit une entité Project en ProjectResponseDTO
     */
    private ProjectResponseDTO convertToDTO(Project project) {
        ProjectResponseDTO dto = new ProjectResponseDTO();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setCode(project.getCode());
        dto.setDepartmentId(project.getDepartmentId());
        dto.setStatus(project.getStatus());
        dto.setDescription(project.getDescription());
        dto.setBudget(project.getBudget());
        dto.setStartDate(project.getStartDate());
        dto.setEndDate(project.getEndDate());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        return dto;
    }

    /**
     * Récupère TOUS les projets d'un employé (sans filtre de statut)
     */
    public List<ProjectResponseDTO> getAllEmployeeProjects(String employeeId) {
        List<EmployeeProjectAssignment> assignments =
                assignmentRepository.findByEmployeeId(employeeId);

        return assignments.stream()
                .map(EmployeeProjectAssignment::getProject)
                .map(this::convertToDTO)  // Ne pas filtrer par statut
                .collect(Collectors.toList());
    }
}