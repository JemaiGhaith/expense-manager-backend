package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.CategoryDTO;
import com.coralio.expense_management_microservice.dto.CategoryRequest;
import com.coralio.expense_management_microservice.dto.ColumnInfoDTO;
import com.coralio.expense_management_microservice.services.CategoryService;
import com.coralio.expense_management_microservice.services.DatabaseMigrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categories/admin")
public class CategoryController {
    private final CategoryService categoryService;
    private final DatabaseMigrationService databaseMigrationService;

    public CategoryController(CategoryService categoryService,
                              DatabaseMigrationService databaseMigrationService) {
        this.categoryService = categoryService;
        this.databaseMigrationService = databaseMigrationService;
    }

    // ✅ CREATE
    @PostMapping
    public ResponseEntity<CategoryDTO> createCategory(@RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createCategory(request));
    }

    // ✅ UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<CategoryDTO> updateCategory(
            @PathVariable Long id,
            @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    // ✅ GET ALL
    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    // ✅ GET BY ID
    @GetMapping("/{id}")
    public ResponseEntity<CategoryDTO> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    // ✅ DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    // ✅ NOUVEAU - Retourne les colonnes AVEC leur type
    @GetMapping("/available-columns-with-type")
    public ResponseEntity<List<ColumnInfoDTO>> getAvailableColumnsWithType() {
        List<ColumnInfoDTO> columns = databaseMigrationService.getDynamicColumnsWithType();
        return ResponseEntity.ok(columns);
    }

    // ✅ Ancien endpoint pour compatibilité
    @GetMapping("/available-columns")
    public ResponseEntity<List<String>> getAvailableColumns() {
        List<String> columns = databaseMigrationService.getDynamicColumns();
        return ResponseEntity.ok(columns);
    }
}