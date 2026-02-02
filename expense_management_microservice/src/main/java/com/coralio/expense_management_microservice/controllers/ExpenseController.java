package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.ExpenseRequest;
import com.coralio.expense_management_microservice.entities.ExpenseLine;
import com.coralio.expense_management_microservice.entities.ExpenseNote;
import com.coralio.expense_management_microservice.entities.ExpenseStatus;
import com.coralio.expense_management_microservice.entities.Project;
import com.coralio.expense_management_microservice.repos.ExpenseLineRepository;
import com.coralio.expense_management_microservice.services.ExpenseService;
import com.coralio.expense_management_microservice.services.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {
    private final ExpenseLineRepository expenseLineRepository;

    private final ExpenseService expenseService;
    private final ProjectService projectService;

    public ExpenseController(ExpenseService expenseService, ProjectService projectService, ExpenseLineRepository expenseLineRepository) {
        this.expenseService = expenseService;
        this.projectService = projectService;

        this.expenseLineRepository = expenseLineRepository;

    }

    @PostMapping
    public ResponseEntity<ExpenseNote> createNote(@RequestBody ExpenseRequest request) {
        ExpenseNote note = request.getNote();
        List<ExpenseLine> lines = request.getLines();
        return ResponseEntity.ok(expenseService.createExpenseNote(note, lines));
    }
    @GetMapping
    public ResponseEntity<List<ExpenseNote>> getAllNotes() {
        return ResponseEntity.ok(expenseService.getAllNotes());
    }

    /*@GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<ExpenseNote>> getNotesByEmployee(@PathVariable String employeeId) {
        return ResponseEntity.ok(expenseService.getNotesByEmployee(employeeId));
    }*/
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<Map<String, Object>>> getNotesByEmployee(
            @PathVariable String employeeId) {

        List<ExpenseNote> notes = expenseService.getNotesByEmployee(employeeId);

        List<Map<String, Object>> response = notes.stream().map(note -> {
            Map<String, Object> map = new HashMap<>();

            map.put("id", note.getId());
            map.put("employeeId", note.getEmployeeId());
            map.put("projectId", note.getProjectId());
            map.put("createdAt", note.getCreatedAt());
            map.put("totalAmount", note.getTotalAmount());
            map.put("status", note.getStatus());

            // 🔹 ICI on ajoute le nom du projet
            projectService.getProjectById(note.getProjectId())
                    .ifPresentOrElse(
                            project -> map.put("projectName", project.getName()),
                            () -> map.put("projectName", "Projet inconnu")
                    );

            return map;
        }).toList();

        return ResponseEntity.ok(response);
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




    @GetMapping("/{noteId}/lines")
    public ResponseEntity<List<ExpenseLine>> getLinesByNote(@PathVariable Long noteId) {
        List<ExpenseLine> lines = expenseLineRepository.findByExpenseNoteId(noteId);
        return ResponseEntity.ok(lines);
    }

}
