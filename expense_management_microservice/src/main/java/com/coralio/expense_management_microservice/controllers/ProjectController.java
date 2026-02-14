package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.ProjectResponseDTO;
import com.coralio.expense_management_microservice.enums.ProjectStatus;
import com.coralio.expense_management_microservice.services.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    // 🔒 READ-ONLY pour les employés
    // Les opérations d'écriture sont dans /api/admin/projects

    @GetMapping
    public ResponseEntity<List<ProjectResponseDTO>> getProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> getProjectById(@PathVariable Long id) {
        return projectService.getProjectById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ProjectResponseDTO>> getProjectsByStatus(@PathVariable String status) {
        // Conversion simple pour les employés
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    // ✅ NOUVEAU - Récupérer les projets par département
    @GetMapping("/by-department/{departmentId}")
    public ResponseEntity<List<ProjectResponseDTO>> getProjectsByDepartment(@PathVariable Long departmentId) {
        return ResponseEntity.ok(projectService.getProjectsByDepartment(departmentId));
    }

    // ✅ NOUVEAU - Récupérer les projets actifs par département
    @GetMapping("/by-department/{departmentId}/active")
    public ResponseEntity<List<ProjectResponseDTO>> getActiveProjectsByDepartment(@PathVariable Long departmentId) {
        return ResponseEntity.ok(
                projectService.getProjectsByDepartment(departmentId)
                        .stream()
                        .filter(project -> project.getStatus() == ProjectStatus.ACTIF)
                        .collect(Collectors.toList())
        );
    }
}