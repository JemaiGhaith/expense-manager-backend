package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.services.EmployeeProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}