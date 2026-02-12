package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.DepartmentRequestDTO;
import com.coralio.expense_management_microservice.dto.DepartmentResponseDTO;
import com.coralio.expense_management_microservice.enums.DepartmentStatus;
import com.coralio.expense_management_microservice.services.DepartmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/departments")
public class DepartmentAdminController {

    private final DepartmentService departmentService;

    public DepartmentAdminController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    // ==================== CRUD ====================

    @PostMapping
    public ResponseEntity<?> createDepartment(@RequestBody DepartmentRequestDTO request) {
        try {
            DepartmentResponseDTO created = departmentService.createDepartment(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping
    public ResponseEntity<List<DepartmentResponseDTO>> getAllDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDepartmentById(@PathVariable Long id) {
        return departmentService.getDepartmentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDepartment(@PathVariable Long id, @RequestBody DepartmentRequestDTO request) {
        try {
            DepartmentResponseDTO updated = departmentService.updateDepartment(id, request);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDepartment(@PathVariable Long id) {
        try {
            departmentService.deleteDepartment(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // ==================== STATUS MANAGEMENT ====================

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateDepartmentStatus(
            @PathVariable Long id,
            @RequestParam DepartmentStatus status) {
        try {
            DepartmentResponseDTO updated = departmentService.updateDepartmentStatus(id, status);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // ==================== FILTERS & SEARCH ====================

    @GetMapping("/status/{status}")
    public ResponseEntity<List<DepartmentResponseDTO>> getDepartmentsByStatus(@PathVariable DepartmentStatus status) {
        return ResponseEntity.ok(departmentService.getDepartmentsByStatus(status));
    }

    @GetMapping("/active")
    public ResponseEntity<List<DepartmentResponseDTO>> getActiveDepartments() {
        return ResponseEntity.ok(departmentService.getActiveDepartments());
    }

    @GetMapping("/search")
    public ResponseEntity<List<DepartmentResponseDTO>> searchDepartments(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(departmentService.searchDepartments(q));
    }
}