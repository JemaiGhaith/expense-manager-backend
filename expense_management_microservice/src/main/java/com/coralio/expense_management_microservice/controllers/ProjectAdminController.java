package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.ProjectRequestDTO;
import com.coralio.expense_management_microservice.dto.ProjectResponseDTO;
import com.coralio.expense_management_microservice.enums.ProjectStatus;
import com.coralio.expense_management_microservice.services.EmployeeProjectService;
import com.coralio.expense_management_microservice.services.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/projects")
public class ProjectAdminController {

    private final ProjectService projectService;
    private final EmployeeProjectService employeeProjectService;

    public ProjectAdminController(ProjectService projectService,
                                  EmployeeProjectService employeeProjectService) {
        this.projectService = projectService;
        this.employeeProjectService = employeeProjectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponseDTO> createProject(@RequestBody ProjectRequestDTO request) {
        ProjectResponseDTO created = projectService.createProject(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> updateProject(@PathVariable Long id,
                                                            @RequestBody ProjectRequestDTO request) {
        ProjectResponseDTO updated = projectService.updateProject(id, request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProjectResponseDTO> updateStatus(@PathVariable Long id,
                                                           @RequestParam ProjectStatus status) {
        ProjectResponseDTO updated = projectService.updateProjectStatus(id, status);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponseDTO>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponseDTO> getProjectById(@PathVariable Long id) {
        return projectService.getProjectById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Endpoint pour récupérer les IDs des employés affectés à un projet
    @GetMapping("/{projectId}/employees")
    public ResponseEntity<List<String>> getProjectEmployees(@PathVariable Long projectId) {
        List<String> employeeIds = employeeProjectService.getEmployeesForProject(projectId);
        return ResponseEntity.ok(employeeIds);
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<ProjectResponseDTO>> getProjectsByDepartment(
            @PathVariable Long departmentId) {

        // ✅ getProjectsByDepartment retourne déjà List<ProjectResponseDTO>
        List<ProjectResponseDTO> projects = projectService.getProjectsByDepartment(departmentId);

        return ResponseEntity.ok(projects);
    }
}