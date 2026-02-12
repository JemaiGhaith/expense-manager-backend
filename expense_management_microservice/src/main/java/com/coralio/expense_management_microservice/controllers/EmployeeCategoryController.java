package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.CategoryDTO;
import com.coralio.expense_management_microservice.services.EmployeeCategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/categories/employee")
public class EmployeeCategoryController {

    private final EmployeeCategoryService employeeCategoryService;

    public EmployeeCategoryController(EmployeeCategoryService employeeCategoryService) {
        this.employeeCategoryService = employeeCategoryService;
    }

    // 🔥 Endpoint spécial employés - retourne les catégories avec LEURS CHAMPS
    @GetMapping("/active")
    public ResponseEntity<List<CategoryDTO>> getActiveCategoriesForEmployees() {
        return ResponseEntity.ok(employeeCategoryService.getActiveCategoriesForEmployees());
    }
}