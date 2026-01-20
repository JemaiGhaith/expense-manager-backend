package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.ExpenseRequest;
import com.coralio.expense_management_microservice.entities.ExpenseLine;
import com.coralio.expense_management_microservice.entities.ExpenseNote;
import com.coralio.expense_management_microservice.entities.ExpenseStatus;
import com.coralio.expense_management_microservice.services.ExpenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<ExpenseNote> createNote(@RequestBody ExpenseRequest request) {
        ExpenseNote note = request.getNote();
        List<ExpenseLine> lines = request.getLines();
        return ResponseEntity.ok(expenseService.createExpenseNote(note, lines));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<ExpenseNote>> getNotesByEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(expenseService.getNotesByEmployee(employeeId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ExpenseNote>> getNotesByStatus(@PathVariable String status) {
        ExpenseStatus enumStatus;
        try {
            enumStatus = ExpenseStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build(); // Si valeur invalide
        }
        return ResponseEntity.ok(expenseService.getNotesByStatus(enumStatus));
    }


    @PutMapping("/validate/{noteId}")
    public ResponseEntity<ExpenseNote> validateNote(@PathVariable Long noteId) {
        return ResponseEntity.ok(expenseService.validateNote(noteId));
    }

    @PutMapping("/refuse/{noteId}")
    public ResponseEntity<ExpenseNote> refuseNote(@PathVariable Long noteId, @RequestParam String comment) {
        return ResponseEntity.ok(expenseService.refuseNote(noteId, comment));
    }

    @GetMapping("/lines/{noteId}")
    public ResponseEntity<List<ExpenseLine>> getLines(@PathVariable Long noteId) {
        return ResponseEntity.ok(expenseService.getLines(noteId));
    }
}
