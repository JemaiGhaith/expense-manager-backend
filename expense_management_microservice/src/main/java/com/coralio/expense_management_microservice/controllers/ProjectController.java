package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.ProjectResponseDTO;
import com.coralio.expense_management_microservice.enums.ProjectStatus;
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

    public ProjectController(ProjectService projectService,
                             EmployeeProjectService employeeProjectService) {
        this.projectService = projectService;
        this.employeeProjectService = employeeProjectService;
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponseDTO>> getProjects(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null) {
            return ResponseEntity.badRequest().build(); // ou liste vide
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
}