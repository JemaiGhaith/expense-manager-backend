package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.DepartmentResponseDTO;
import com.coralio.expense_management_microservice.services.DepartmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    // 🔒 READ-ONLY pour les employés

    @GetMapping
    public ResponseEntity<List<DepartmentResponseDTO>> getAllDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    @GetMapping("/active")
    public ResponseEntity<List<DepartmentResponseDTO>> getActiveDepartments() {
        return ResponseEntity.ok(departmentService.getActiveDepartments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponseDTO> getDepartmentById(@PathVariable Long id) {
        return departmentService.getDepartmentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}