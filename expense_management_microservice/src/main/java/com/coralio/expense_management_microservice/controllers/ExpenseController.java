package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.ExpenseLineDetailDTO;
import com.coralio.expense_management_microservice.dto.ExpenseRequest;
import com.coralio.expense_management_microservice.entities.*;
import com.coralio.expense_management_microservice.repos.ExpenseDuplicateRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {
    private final ExpenseLineRepository expenseLineRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ExpenseService expenseService;
    private final ProjectService projectService;
    @Autowired
    private ExpenseDuplicateRepository duplicateRepository;
    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ObjectMapper objectMapper;

    public ExpenseController(
            ExpenseService expenseService,
            ProjectService projectService,
            ExpenseLineRepository expenseLineRepository,
            JdbcTemplate jdbcTemplate) {
        this.expenseService = expenseService;
        this.projectService = projectService;
        this.expenseLineRepository = expenseLineRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // =========================
    // UPLOAD NOTE + FICHIERS (ACCORD + FACTURES)
    // =========================
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadExpense(
            @RequestPart("note") String noteJson,
            @RequestPart("lines") String linesJson,
            @RequestPart(value = "accordFile", required = true) MultipartFile accordFile,
            @RequestPart(value = "factureFiles", required = true) List<MultipartFile> factureFiles
    ) {
        try {
            ExpenseNote note = objectMapper.readValue(noteJson, ExpenseNote.class);
            List<ExpenseLine> lines = objectMapper.readValue(
                    linesJson,
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, ExpenseLine.class)
            );

            String accordFileName = fileStorageService.storeFile(accordFile, note.getEmployeeId(), "accords");
            note.setAccordPath(accordFileName);

            List<String> factureFileNames = new ArrayList<>();
            for (MultipartFile file : factureFiles) {
                String savedFileName = fileStorageService.storeFile(file, note.getEmployeeId(), "factures");
                factureFileNames.add(savedFileName);
            }

            ExpenseNote createdNote = expenseService.createExpenseNoteWithFiles(
                    note,
                    lines,
                    accordFileName,
                    factureFileNames
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(createdNote);

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // =========================
    // DOWNLOAD FICHIER
    // =========================
    @GetMapping("/files/{employeeId}/{type}/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String employeeId,
            @PathVariable String type,
            @PathVariable String filename
    ) {
        try {
            String filePath = type + "/" + filename;
            Resource resource = fileStorageService.loadFileAsResource(employeeId, filePath);

            String contentType = determineContentType(filename);

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

    @GetMapping("/files/{employeeId}/{filename:.+}")
    public ResponseEntity<Resource> downloadFileOld(
            @PathVariable String employeeId,
            @PathVariable String filename
    ) {
        try {
            String filePath = "factures/" + filename;
            Resource resource = fileStorageService.loadFileAsResource(employeeId, filePath);

            String contentType = determineContentType(filename);

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

    @GetMapping("/{noteId}/files-urls")
    public ResponseEntity<Map<String, Object>> getFilesUrls(@PathVariable Long noteId) {
        try {
            ExpenseNote note = expenseService.getNoteWithLines(noteId);
            List<ExpenseLine> lines = expenseService.getLines(noteId);

            Map<String, Object> response = new HashMap<>();

            if (note.getAccordPath() != null) {
                String accordUrl = "/api/expenses/files/" + note.getEmployeeId() + "/" + note.getAccordPath();
                response.put("accordUrl", accordUrl);
                response.put("accordPath", note.getAccordPath());
            }

            List<Map<String, Object>> facturesUrls = new ArrayList<>();
            for (ExpenseLine line : lines) {
                if (line.getJustificatifPath() != null) {
                    Map<String, Object> factureInfo = new HashMap<>();
                    factureInfo.put("lineId", line.getId());
                    factureInfo.put("factureUrl", "/api/expenses/files/" + note.getEmployeeId() + "/" + line.getJustificatifPath());
                    factureInfo.put("facturePath", line.getJustificatifPath());
                    facturesUrls.add(factureInfo);
                }
            }
            response.put("factures", facturesUrls);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<?> createNote(@RequestBody ExpenseRequest request) {
        try {
            ExpenseNote note = request.getNote();
            List<ExpenseLine> lines = request.getLines();
            return ResponseEntity.ok(expenseService.createExpenseNote(note, lines));

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<ExpenseNote>> getAllNotes() {
        return ResponseEntity.ok(expenseService.getAllNotes());
    }

    // ========== ENDPOINTS GET AVEC NOUVEAUX CHAMPS ==========

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
            map.put("accordPath", note.getAccordPath());
            map.put("noteDescription", note.getNoteDescription());
            // ✅ NOUVEAUX CHAMPS
            map.put("decisionComment", note.getDecisionComment());
            map.put("decidedBy", note.getDecidedBy());
            map.put("decidedAt", note.getDecidedAt());
            map.put("managerId", note.getManagerId()); // ✅ AJOUTÉ

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
    public ResponseEntity<List<Map<String, Object>>> getNotesByStatus(@PathVariable String status) {
        ExpenseStatus enumStatus;
        try {
            enumStatus = ExpenseStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        List<ExpenseNote> notes = expenseService.getNotesByStatus(enumStatus);

        List<Map<String, Object>> response = notes.stream().map(note -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", note.getId());
            map.put("employeeId", note.getEmployeeId());
            map.put("projectId", note.getProjectId());
            map.put("createdAt", note.getCreatedAt());
            map.put("totalAmount", note.getTotalAmount());
            map.put("status", note.getStatus());
            map.put("accordPath", note.getAccordPath());

            // ✅ NOUVEAUX CHAMPS
            map.put("decisionComment", note.getDecisionComment());
            map.put("decidedBy", note.getDecidedBy());
            map.put("decidedAt", note.getDecidedAt());
            map.put("managerId", note.getManagerId()); // ✅ AJOUTÉ

            return map;
        }).toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<Map<String, Object>>> getNotesByDepartment(
            @PathVariable Long departmentId) {

        try {
            List<ExpenseNote> notes = expenseService.getNotesByDepartment(departmentId);

            List<Map<String, Object>> response = notes.stream().map(note -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", note.getId());
                map.put("employeeId", note.getEmployeeId());
                map.put("projectId", note.getProjectId());
                map.put("createdAt", note.getCreatedAt());
                map.put("totalAmount", note.getTotalAmount());
                map.put("status", note.getStatus());
                map.put("accordPath", note.getAccordPath());

                // ✅ NOUVEAUX CHAMPS
                map.put("decisionComment", note.getDecisionComment());
                map.put("decidedBy", note.getDecidedBy());
                map.put("decidedAt", note.getDecidedAt());
                map.put("managerId", note.getManagerId()); // ✅ AJOUTÉ

                projectService.getProjectById(note.getProjectId())
                        .ifPresentOrElse(
                                project -> map.put("projectName", project.getName()),
                                () -> map.put("projectName", "Projet inconnu")
                        );

                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== NOUVEAUX ENDPOINTS POUR MANAGER ==========

    @PutMapping("/manager/validate/{noteId}")
    public ResponseEntity<ExpenseNote> managerValidate(
            @PathVariable Long noteId,
            @RequestParam String comment,
            @RequestParam String managerId,
            @RequestParam String managerName) {
        return ResponseEntity.ok(expenseService.managerValidateNote(noteId, comment, managerId, managerName));
    }

    @PutMapping("/manager/reject/{noteId}")
    public ResponseEntity<ExpenseNote> managerReject(
            @PathVariable Long noteId,
            @RequestParam String comment,
            @RequestParam String managerId,
            @RequestParam String managerName) {
        return ResponseEntity.ok(expenseService.managerRejectNote(noteId, comment, managerId, managerName));
    }

    // ========== NOUVEAUX ENDPOINTS POUR ADMIN ==========

    @PutMapping("/admin/reject/{noteId}")
    public ResponseEntity<?> adminReject(
            @PathVariable Long noteId,
            @RequestParam String comment) {
        try {
            if (comment == null || comment.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Le commentaire est obligatoire pour le refus"));
            }
            ExpenseNote updatedNote = expenseService.adminRejectNote(noteId, comment);
            return ResponseEntity.ok(updatedNote);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/admin/reimburse/{noteId}")
    public ResponseEntity<?> adminReimburse(
            @PathVariable Long noteId,
            @RequestParam(required = false) String comment) {
        try {
            ExpenseNote updatedNote = expenseService.adminReimburseNote(noteId, comment);
            return ResponseEntity.ok(updatedNote);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ========== ENDPOINTS EXISTANTS CONSERVÉS POUR COMPATIBILITÉ ==========

    @PutMapping("/validate/{noteId}")
    public ResponseEntity<ExpenseNote> validateNote(@PathVariable Long noteId) {
        return ResponseEntity.ok(expenseService.validateNote(noteId));
    }

    @PutMapping("/validate/{noteId}/with-comment")
    public ResponseEntity<ExpenseNote> validateNoteWithComment(
            @PathVariable Long noteId,
            @RequestParam(required = false) String comment) {
        return ResponseEntity.ok(expenseService.validateNote(noteId, comment));
    }

    @PutMapping("/refuse/{noteId}")
    public ResponseEntity<ExpenseNote> refuseNote(@PathVariable Long noteId, @RequestParam String comment) {
        return ResponseEntity.ok(expenseService.refuseNote(noteId, comment));
    }

    // ========== AUTRES ENDPOINTS EXISTANTS ==========

    @GetMapping("/lines/{noteId}")
    public ResponseEntity<List<ExpenseLine>> getLines(@PathVariable Long noteId) {
        return ResponseEntity.ok(expenseService.getLines(noteId));
    }

    @GetMapping("/{noteId}/lines")
    public ResponseEntity<List<ExpenseLineDetailDTO>> getLinesByNote(@PathVariable Long noteId) {
        try {
            List<Map<String, Object>> rows = expenseLineRepository.findByExpenseNoteIdNative(noteId);
            List<ExpenseLineDetailDTO> result = new ArrayList<>();

            for (Map<String, Object> row : rows) {
                ExpenseLineDetailDTO dto = new ExpenseLineDetailDTO();

                dto.setId(((Number) row.get("id")).longValue());
                dto.setExpenseNoteId(((Number) row.get("expense_note_id")).longValue());
                dto.setCategoryId(row.get("category_id") != null ? ((Number) row.get("category_id")).longValue() : null);
                dto.setAmount(row.get("amount") != null ? ((Number) row.get("amount")).doubleValue() : null);

                if (row.get("expense_date") != null) {
                    dto.setExpenseDate(LocalDate.parse(row.get("expense_date").toString()));
                }

                dto.setDescription((String) row.get("description"));
                dto.setJustificatifPath((String) row.get("justificatif_path"));

                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String columnName = entry.getKey();
                    Object value = entry.getValue();

                    if (!isStandardColumn(columnName) && value != null) {
                        String fieldName = toCamelCase(columnName);
                        dto.setDynamicField(fieldName, value);
                        dto.setDynamicField(columnName, value);
                    }
                }

                result.add(dto);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la récupération des lignes: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{noteId}")
    public ResponseEntity<ExpenseNote> getNoteById(@PathVariable Long noteId) {
        return ResponseEntity.ok(expenseService.getNoteWithLines(noteId));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<?> deleteNote(
            @PathVariable Long noteId,
            @RequestParam String employeeId) {
        try {
            expenseService.deleteNote(noteId, employeeId);
            return ResponseEntity.ok(Map.of("message", "Note supprimée avec succès"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur lors de la suppression"));
        }
    }

    @PutMapping(value = "/{noteId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateExpense(
            @PathVariable Long noteId,
            @RequestParam String employeeId,
            @RequestPart("note") String noteJson,
            @RequestPart("lines") String linesJson,
            @RequestPart(value = "accordFile", required = false) MultipartFile accordFile,
            @RequestParam Map<String, MultipartFile> allFiles
    ) {
        try {
            ExpenseNote updatedNote = objectMapper.readValue(noteJson, ExpenseNote.class);
            List<ExpenseLine> updatedLines = objectMapper.readValue(
                    linesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ExpenseLine.class)
            );

            List<MultipartFile> factureFiles = new ArrayList<>();
            for (int i = 0; i < updatedLines.size(); i++) {
                MultipartFile file = allFiles.get("factureFile_" + i);
                factureFiles.add(file);
            }

            ExpenseNote result = expenseService.updateExpenseNoteWithFiles(
                    noteId, employeeId, updatedNote, updatedLines, accordFile, factureFiles
            );

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ========== MÉTHODES UTILITAIRES ==========

    private boolean isStandardColumn(String columnName) {
        return columnName.equals("id") ||
                columnName.equals("expense_note_id") ||
                columnName.equals("category_id") ||
                columnName.equals("amount") ||
                columnName.equals("expense_date") ||
                columnName.equals("description") ||
                columnName.equals("justificatif_path") ||
                columnName.equals("created_at") ||
                columnName.equals("updated_at");
    }

    private String toCamelCase(String snakeCase) {
        if (snakeCase == null) return null;
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(c);
                }
            }
        }
        return result.toString();
    }

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



    // ✅ Sauvegarder les doublons détectés (appelé par Angular après soumission)
    @PostMapping("/{noteId}/duplicates")
    public ResponseEntity<?> saveDuplicates(
            @PathVariable Long noteId,
            @RequestBody List<Map<String, Object>> duplicates
    ) {
        try {
            // Supprimer les anciens doublons pour cette note (en cas de re-soumission)
            duplicateRepository.deleteByExpenseNoteId(noteId);

            List<ExpenseDuplicate> saved = new ArrayList<>();

            for (Map<String, Object> d : duplicates) {
                ExpenseDuplicate dup = new ExpenseDuplicate();
                dup.setExpenseNoteId(noteId);

                // expenseLineId peut être null (pour l'accord)
                if (d.get("expenseLineId") != null) {
                    dup.setExpenseLineId(Long.parseLong(d.get("expenseLineId").toString()));
                }

                // ✅ On sauvegarde uniquement les chemins (pas les fichiers)
                dup.setUploadedFile(d.get("uploadedFile") != null ? d.get("uploadedFile").toString() : null);
                dup.setDuplicateFile(d.get("duplicateFile") != null ? d.get("duplicateFile").toString() : null);
                dup.setSimilarity(d.get("similarity") != null ? Double.parseDouble(d.get("similarity").toString()) : null);

                saved.add(duplicateRepository.save(dup));
            }

            System.out.println("✅ " + saved.size() + " doublon(s) sauvegardé(s) pour note #" + noteId);
            return ResponseEntity.ok(Map.of("saved", saved.size()));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ✅ Récupérer les doublons d'une note (pour le manager)
    @GetMapping("/{noteId}/duplicates")
    public ResponseEntity<List<ExpenseDuplicate>> getDuplicates(@PathVariable Long noteId) {
        return ResponseEntity.ok(duplicateRepository.findByExpenseNoteId(noteId));
    }
}