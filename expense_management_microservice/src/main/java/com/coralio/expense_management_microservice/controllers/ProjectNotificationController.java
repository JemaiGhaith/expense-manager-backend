package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.entities.ExpenseNote;
import com.coralio.expense_management_microservice.entities.ExpenseStatus;
import com.coralio.expense_management_microservice.repos.ExpenseNoteRepository;
import com.coralio.expense_management_microservice.services.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/notifications")
public class ProjectNotificationController {

    private final ProjectService projectService;
    private final ExpenseNoteRepository expenseNoteRepository;

    public ProjectNotificationController(ProjectService projectService,
                                         ExpenseNoteRepository expenseNoteRepository) {
        this.projectService = projectService;
        this.expenseNoteRepository = expenseNoteRepository;
    }

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