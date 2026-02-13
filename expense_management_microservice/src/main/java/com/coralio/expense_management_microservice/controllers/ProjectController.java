package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.ProjectResponseDTO;
import com.coralio.expense_management_microservice.services.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}