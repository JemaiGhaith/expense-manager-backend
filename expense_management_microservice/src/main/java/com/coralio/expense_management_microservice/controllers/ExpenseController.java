package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.ExpenseRequest;
import com.coralio.expense_management_microservice.entities.ExpenseLine;
import com.coralio.expense_management_microservice.entities.ExpenseNote;
import com.coralio.expense_management_microservice.entities.ExpenseStatus;
import com.coralio.expense_management_microservice.entities.Project;
import com.coralio.expense_management_microservice.repos.ExpenseLineRepository;
import com.coralio.expense_management_microservice.services.ExpenseService;
import com.coralio.expense_management_microservice.services.FileStorageService;
import com.coralio.expense_management_microservice.services.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {
    private final ExpenseLineRepository expenseLineRepository;

    private final ExpenseService expenseService;
    private final ProjectService projectService;
    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ObjectMapper objectMapper;

    // ====================
    // ENDPOINTS AVEC FICHIERS
    // ====================


    // =========================
    // UPLOAD NOTE + FICHIERS
    // =========================
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadExpense(
            @RequestPart("note") String noteJson,
            @RequestPart("lines") String linesJson,
            @RequestPart("files") List<MultipartFile> files
    ) {
        try {
            ExpenseNote note = objectMapper.readValue(noteJson, ExpenseNote.class);
            List<ExpenseLine> lines = objectMapper.readValue(
                    linesJson,
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, ExpenseLine.class)
            );

            List<String> fileNames = new ArrayList<>();

            for (MultipartFile file : files) {
                String savedFileName =
                        fileStorageService.storeFile(file, note.getEmployeeId());
                fileNames.add(savedFileName);
            }

            ExpenseNote createdNote =
                    expenseService.createExpenseNoteWithFiles(note, lines, fileNames);

            return ResponseEntity.status(HttpStatus.CREATED).body(createdNote);

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    // =========================
    // DOWNLOAD FICHIER
    // =========================
    @GetMapping("/files/{employeeId}/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String employeeId,
            @PathVariable String filename
    ) {
        try {
            Resource resource =
                    fileStorageService.loadFileAsResource(employeeId, filename);

            String contentType = determineContentType(filename);

            // "inline" ouvre dans le navigateur si supporté
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
    }


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

    // ====================
    // MÉTHODES UTILITAIRES
    // ====================

    private String determineContentType(String filename) {
        if (filename.toLowerCase().endsWith(".pdf")) {
            return "application/pdf";
        } else if (filename.toLowerCase().endsWith(".jpg") ||
                filename.toLowerCase().endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.toLowerCase().endsWith(".png")) {
            return "image/png";
        } else if (filename.toLowerCase().endsWith(".doc")) {
            return "application/msword";
        } else if (filename.toLowerCase().endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else {
            return "application/octet-stream";
        }
    }

    private boolean isValidFileType(String contentType) {
        if (contentType == null) return false;

        return contentType.equals("application/pdf") ||
                contentType.equals("image/jpeg") ||
                contentType.equals("image/jpg") ||
                contentType.equals("image/png") ||
                contentType.equals("application/msword") ||
                contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }
}
