package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.ProjectResponseDTO;
import com.coralio.expense_management_microservice.entities.ExpenseNote;
import com.coralio.expense_management_microservice.entities.ExpenseStatus;
import com.coralio.expense_management_microservice.enums.ProjectStatus;
import com.coralio.expense_management_microservice.repos.ExpenseNoteRepository;
import com.coralio.expense_management_microservice.services.EmployeeProjectService;
import com.coralio.expense_management_microservice.services.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final EmployeeProjectService employeeProjectService;
    private final ExpenseNoteRepository expenseNoteRepository;

    public ProjectController(ProjectService projectService,
                             EmployeeProjectService employeeProjectService,
                             ExpenseNoteRepository expenseNoteRepository) {
        this.projectService = projectService;
        this.employeeProjectService = employeeProjectService;
        this.expenseNoteRepository = expenseNoteRepository;
    }

    // ========== ENDPOINTS EXISTANTS (avec X-Employee-Id) ==========

    @GetMapping
    public ResponseEntity<List<ProjectResponseDTO>> getProjects(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null) {
            return ResponseEntity.badRequest().build();
        }
        List<ProjectResponseDTO> projects = employeeProjectService.getProjectsForEmployee(employeeId)
                .stream()
                .map(projectService::convertToResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> getProjectById(
            @PathVariable Long id,
            @RequestHeader("X-Employee-Id") String employeeId) {
        return employeeProjectService.getProjectsForEmployee(employeeId).stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .map(projectService::convertToResponseDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ProjectResponseDTO>> getProjectsByStatus(
            @PathVariable String status,
            @RequestHeader("X-Employee-Id") String employeeId) {
        List<ProjectResponseDTO> projects = employeeProjectService.getProjectsForEmployee(employeeId)
                .stream()
                .map(projectService::convertToResponseDTO)
                .filter(p -> p.getStatus().name().equalsIgnoreCase(status))
                .collect(Collectors.toList());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/by-department/{departmentId}")
    public ResponseEntity<List<ProjectResponseDTO>> getProjectsByDepartment(
            @PathVariable Long departmentId,
            @RequestHeader("X-Employee-Id") String employeeId) {
        List<ProjectResponseDTO> projects = employeeProjectService.getProjectsForEmployee(employeeId)
                .stream()
                .map(projectService::convertToResponseDTO)
                .filter(p -> p.getDepartmentId().equals(departmentId))
                .collect(Collectors.toList());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/by-department/{departmentId}/active")
    public ResponseEntity<List<ProjectResponseDTO>> getActiveProjectsByDepartment(
            @PathVariable Long departmentId,
            @RequestHeader("X-Employee-Id") String employeeId) {
        List<ProjectResponseDTO> projects = employeeProjectService.getProjectsForEmployee(employeeId)
                .stream()
                .map(projectService::convertToResponseDTO)
                .filter(p -> p.getDepartmentId().equals(departmentId) && p.getStatus() == ProjectStatus.ACTIF)
                .collect(Collectors.toList());
        return ResponseEntity.ok(projects);
    }

    // ========== ENDPOINTS PUBLICS (sans authentification) ==========

    @GetMapping("/public/{id}")
    public ResponseEntity<ProjectResponseDTO> getProjectByIdPublic(@PathVariable Long id) {
        return projectService.getProjectById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/public/by-department/{departmentId}")
    public ResponseEntity<List<ProjectResponseDTO>> getProjectsByDepartmentPublic(
            @PathVariable Long departmentId) {
        List<ProjectResponseDTO> projects = projectService.getProjectsByDepartment(departmentId);
        return ResponseEntity.ok(projects);
    }

    // ========== ENDPOINTS POUR NOTIFICATIONS ==========

    @GetMapping("/{projectId}/department")
    public ResponseEntity<Long> getProjectDepartment(@PathVariable Long projectId) {
        return projectService.getProjectById(projectId)
                .map(project -> ResponseEntity.ok(project.getDepartmentId()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{projectId}/remaining-budget")
    public ResponseEntity<Double> getRemainingBudget(@PathVariable Long projectId) {
        return projectService.getProjectById(projectId)
                .map(project -> {
                    Double totalExpenses = expenseNoteRepository
                            .findByProjectId(projectId)
                            .stream()
                            .filter(note -> note.getStatus() == ExpenseStatus.VALIDEE)
                            .mapToDouble(ExpenseNote::getTotalAmount)
                            .sum();
                    Double remainingBudget = project.getBudget() - totalExpenses;
                    return ResponseEntity.ok(remainingBudget);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{projectId}/name")
    public ResponseEntity<String> getProjectName(@PathVariable Long projectId) {
        return projectService.getProjectById(projectId)
                .map(project -> ResponseEntity.ok(project.getName()))
                .orElse(ResponseEntity.notFound().build());
    }
}