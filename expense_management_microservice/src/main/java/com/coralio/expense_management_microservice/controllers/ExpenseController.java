package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.ExpenseLineDetailDTO;
import com.coralio.expense_management_microservice.dto.ExpenseRequest;
import com.coralio.expense_management_microservice.entities.ExpenseLine;
import com.coralio.expense_management_microservice.entities.ExpenseNote;
import com.coralio.expense_management_microservice.entities.ExpenseStatus;
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

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {
    private final ExpenseLineRepository expenseLineRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ExpenseService expenseService;
    private final ProjectService projectService;

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

            // 1️⃣ Sauvegarder l'accord dans le dossier "accords"
            String accordFileName = fileStorageService.storeFile(accordFile, note.getEmployeeId(), "accords");
            note.setAccordPath(accordFileName);

            // 2️⃣ Sauvegarder les factures dans le dossier "factures"
            List<String> factureFileNames = new ArrayList<>();
            for (MultipartFile file : factureFiles) {
                String savedFileName = fileStorageService.storeFile(file, note.getEmployeeId(), "factures");
                factureFileNames.add(savedFileName);
            }

            // 3️⃣ Créer la note avec ses lignes
            ExpenseNote createdNote = expenseService.createExpenseNoteWithFiles(
                    note,
                    lines,
                    accordFileName,      // L'accord pour la note
                    factureFileNames     // Les factures pour les lignes
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
    // DOWNLOAD FICHIER (avec chemin complet)
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

    // =========================
    // DOWNLOAD FICHIER (pour compatibilité ancienne version)
    // =========================
    @GetMapping("/files/{employeeId}/{filename:.+}")
    public ResponseEntity<Resource> downloadFileOld(
            @PathVariable String employeeId,
            @PathVariable String filename
    ) {
        try {
            // Par défaut, chercher dans le dossier factures
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

    // =========================
    // ENDPOINT POUR AVOIR LES URLs DES FICHIERS
    // =========================
    @GetMapping("/{noteId}/files-urls")
    public ResponseEntity<Map<String, Object>> getFilesUrls(@PathVariable Long noteId) {
        try {
            ExpenseNote note = expenseService.getNoteWithLines(noteId);
            List<ExpenseLine> lines = expenseService.getLines(noteId);

            Map<String, Object> response = new HashMap<>();

            // URL de l'accord
            if (note.getAccordPath() != null) {
                String accordUrl = "/api/expenses/files/" + note.getEmployeeId() + "/" + note.getAccordPath();
                response.put("accordUrl", accordUrl);
                response.put("accordPath", note.getAccordPath());
            }

            // URLs des factures
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
            map.put("managerComment", note.getManagerComment()); // ✅ AJOUTER CETTE LIGNE

            // Ajouter le nom du projet
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
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(expenseService.getNotesByStatus(enumStatus));
    }

    @PutMapping("/validate/{noteId}")
    public ResponseEntity<ExpenseNote> validateNote(@PathVariable Long noteId) {
        return ResponseEntity.ok(expenseService.validateNote(noteId));
    }
    /**
     * ✅ Valide une note avec commentaire optionnel
     */
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

    @GetMapping("/lines/{noteId}")
    public ResponseEntity<List<ExpenseLine>> getLines(@PathVariable Long noteId) {
        return ResponseEntity.ok(expenseService.getLines(noteId));
    }

    @GetMapping("/{noteId}/lines")
    public ResponseEntity<List<ExpenseLineDetailDTO>> getLinesByNote(@PathVariable Long noteId) {
        try {
            List<Map<String, Object>> rows = expenseLineRepository.findByExpenseNoteIdNative(noteId);
            List<ExpenseLineDetailDTO> result = new ArrayList<>();

            System.out.println("✅ Récupération des lignes pour note #" + noteId);
            System.out.println("📊 Nombre de lignes trouvées: " + rows.size());

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

                        System.out.println("   📌 Champ dynamique: " + columnName + " = " + value);
                    }
                }

                result.add(dto);
            }

            System.out.println("✅ " + result.size() + " lignes préparées avec champs dynamiques");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la récupération des lignes: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

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