package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.ProjectResponseDTO;
import com.coralio.expense_management_microservice.services.EmployeeProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@Slf4j
@RestController
@RequestMapping("/api/admin/projects/assignments")
public class AdminProjectAssignmentController {

    private final EmployeeProjectService employeeProjectService;

    public AdminProjectAssignmentController(EmployeeProjectService employeeProjectService) {
        this.employeeProjectService = employeeProjectService;
    }

    @PostMapping
    public ResponseEntity<Void> assignEmployee(@RequestParam String employeeId,
                                               @RequestParam Long projectId) {
        employeeProjectService.assignEmployeeToProject(employeeId, projectId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unassignEmployee(@RequestParam String employeeId,
                                                 @RequestParam Long projectId) {
        employeeProjectService.unassignEmployeeFromProject(employeeId, projectId);
        return ResponseEntity.noContent().build();
    }
    /**
     * Récupère les IDs des employés d'un projet
     */
    @GetMapping("/project/{projectId}/employees")
    public ResponseEntity<List<String>> getProjectEmployees(@PathVariable Long projectId) {
        try {
            List<String> employeeIds = employeeProjectService.getProjectEmployees(projectId);
            return ResponseEntity.ok(employeeIds);
        } catch (Exception e) {
            log.error("❌ Erreur récupération employés du projet {}: {}", projectId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * ✅ Récupère les projets actifs d'un employé
     */
    @GetMapping("/employee/{employeeId}/projects")
    public ResponseEntity<List<ProjectResponseDTO>> getEmployeeProjects(@PathVariable String employeeId) {
        try {
            log.info("🔍 Récupération des projets pour l'employé: {}", employeeId);
            List<ProjectResponseDTO> projects = employeeProjectService.getEmployeeProjects(employeeId);
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            log.error("❌ Erreur récupération projets pour l'employé {}: {}", employeeId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère TOUS les projets d'un employé (actifs, inactifs, clôturés)
     */
    @GetMapping("/employee/{employeeId}/all-projects")
    public ResponseEntity<List<ProjectResponseDTO>> getAllEmployeeProjects(@PathVariable String employeeId) {
        try {
            log.info("🔍 Récupération de tous les projets pour l'employé: {}", employeeId);
            List<ProjectResponseDTO> projects = employeeProjectService.getAllEmployeeProjects(employeeId);
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            log.error("❌ Erreur récupération tous les projets pour {}: {}", employeeId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}