package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.ProjectRequestDTO;
import com.coralio.expense_management_microservice.dto.ProjectResponseDTO;
import com.coralio.expense_management_microservice.enums.ProjectStatus;
import com.coralio.expense_management_microservice.services.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/projects")
public class ProjectAdminController {

    private final ProjectService projectService;

    public ProjectAdminController(ProjectService projectService) {
        this.projectService = projectService;
    }

    // ==================== CRUD ====================

    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody ProjectRequestDTO request) {
        try {
            ProjectResponseDTO created = projectService.createProject(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponseDTO>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProjectById(@PathVariable Long id) {
        return projectService.getProjectById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProject(@PathVariable Long id, @RequestBody ProjectRequestDTO request) {
        try {
            ProjectResponseDTO updated = projectService.updateProject(id, request);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable Long id) {
        try {
            projectService.deleteProject(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // ==================== STATUS MANAGEMENT ====================

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateProjectStatus(
            @PathVariable Long id,
            @RequestParam ProjectStatus status) {
        try {
            ProjectResponseDTO updated = projectService.updateProjectStatus(id, status);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // ==================== FILTERS & SEARCH ====================

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ProjectResponseDTO>> getProjectsByStatus(@PathVariable ProjectStatus status) {
        return ResponseEntity.ok(projectService.getProjectsByStatus(status));
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<ProjectResponseDTO>> getProjectsByDepartment(@PathVariable Long departmentId) {
        return ResponseEntity.ok(projectService.getProjectsByDepartment(departmentId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProjectResponseDTO>> searchProjects(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(projectService.searchProjects(q));
    }
}