package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.entities.ExpenseLine;
import com.coralio.expense_management_microservice.entities.ExpenseNote;
import com.coralio.expense_management_microservice.entities.ExpenseStatus;
import com.coralio.expense_management_microservice.entities.Project;
import com.coralio.expense_management_microservice.repos.ExpenseLineRepository;
import com.coralio.expense_management_microservice.repos.ExpenseNoteRepository;
import com.coralio.expense_management_microservice.repos.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.web.client.RestTemplate;
@Service
public class ExpenseService {

    private final ProjectRepository projectRepository;
    private final ExpenseNoteRepository noteRepository;
    private final ExpenseLineRepository lineRepository;
    private final DatabaseMigrationService migrationService;
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final Set<String> TUNISIA_HOLIDAYS = Set.of(
            "01-01", "14-01", "20-03", "09-04", "01-05",
            "25-07", "13-08", "15-10", "17-12"
    );

    private final Map<String, String> columnTypeCache = new java.util.concurrent.ConcurrentHashMap<>();
    @Autowired
    private OCRService ocrService;
    @Autowired
    private FileStorageService fileStorageService;

    public ExpenseService(
            ExpenseNoteRepository noteRepository,
            ExpenseLineRepository lineRepository,
            DatabaseMigrationService migrationService,
            ProjectRepository projectRepository,
            JdbcTemplate jdbcTemplate) {
        this.noteRepository = noteRepository;
        this.lineRepository = lineRepository;
        this.migrationService = migrationService;
        this.jdbcTemplate = jdbcTemplate;
        this.projectRepository = projectRepository;
    }

    // ========== MÉTHODES EXISTANTES (inchangées) ==========

    @Transactional
    public ExpenseNote createExpenseNote(ExpenseNote note, List<ExpenseLine> lines) {
        return createExpenseNoteWithFiles(note, lines, null, null);
    }

    @Transactional
    public ExpenseNote createExpenseNoteWithFiles(
            ExpenseNote note,
            List<ExpenseLine> lines,
            String accordFileName,
            List<String> factureFileNames
    ) {
        if (note.getStatus() == null) {
            note.setStatus(ExpenseStatus.EN_ATTENTE);
        }

        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        if (accordFileName != null) {
            note.setAccordPath(accordFileName);
        }

        // ✅ validation dates
        for (ExpenseLine line : lines) {
            if (line.getExpenseDate() == null) {
                line.setExpenseDate(LocalDate.now());
            }
            validateExpenseDate(line.getExpenseDate());
        }

        // ✅ sauvegarde note
        ExpenseNote savedNote = noteRepository.save(note);

        // ================== TRAITEMENT DES LIGNES ==================
        for (int i = 0; i < lines.size(); i++) {

            ExpenseLine line = lines.get(i);
            line.setExpenseNoteId(savedNote.getId());

            if (factureFileNames != null && i < factureFileNames.size()) {
                line.setJustificatifPath(factureFileNames.get(i));
            }

            try {

                String extractedText = "";

                if (line.getJustificatifPath() != null) {

                    MultipartFile file = fileStorageService.getFileAsMultipart(
                            note.getEmployeeId(),
                            line.getJustificatifPath()
                    );

                    extractedText = ocrService.extractText(file);
                }

                Map<String, Object> result = restTemplate.postForObject(
                        "http://localhost:9000/analyze-invoice-ai",
                        Map.of(
                                "ocr_text", extractedText,
                                "amount", line.getAmount(),
                                "date", line.getExpenseDate().toString(),
                                "description", line.getDescription(),
                                "currency", "TND"
                        ),
                        Map.class
                );

                Boolean isAnomaly = (Boolean) result.get("anomaly");
                String message = (String) result.get("message");

                line.setIsAnomalyDepense(isAnomaly != null ? isAnomaly : false);
                line.setAnomalyExpenseMessage(message);

            } catch (Exception e) {
                line.setIsAnomalyDepense(false);
                line.setAnomalyExpenseMessage("IA indisponible");
            }

            // ✅ INSERT APRES IA
            insertExpenseLineWithDynamicColumns(line);
        }

        // ================== TOTAL ==================
        double total = lines.stream()
                .mapToDouble(ExpenseLine::getAmount)
                .sum();

        savedNote.setTotalAmount(total);
        savedNote.setUpdatedAt(LocalDateTime.now());

        // ================== IA FAISS ==================
        for (ExpenseLine line : lines) {
            if (line.getJustificatifPath() != null) {

                String text = line.getDescription() != null
                        ? line.getDescription()
                        : "facture";

                String filename = line.getJustificatifPath();
                String filepath = "uploads/" + filename;

                addToFaissIndex(text, filename, filepath);
            }
        }

        // ================== ACCORD FAISS ==================
        if (savedNote.getAccordPath() != null) {

            String filename = savedNote.getAccordPath();
            String filepath = "uploads/" + filename;

            addToFaissIndex("accord", filename, filepath);

            System.out.println("✅ Accord ajouté à FAISS : " + filename);
        }

        return noteRepository.save(savedNote);
    }


    private void addToFaissIndex(String text, String filename, String filepath) {
        try {
            String url = "http://localhost:9000/add-to-index";

            Map<String, Object> body = Map.of(
                    "text", text,
                    "filename", filename,
                    "filepath", filepath
            );

            restTemplate.postForObject(url, body, Map.class);

            System.out.println("✅ Ajouté à FAISS : " + filename);

        } catch (Exception e) {
            System.err.println("❌ Erreur FAISS : " + e.getMessage());
        }
    }
    @Transactional
    public ExpenseNote createExpenseNoteWithFiles(
            ExpenseNote note,
            List<ExpenseLine> lines,
            List<String> fileNames
    ) {
        return createExpenseNoteWithFiles(note, lines, null, fileNames);
    }

    private void insertExpenseLineWithDynamicColumns(ExpenseLine line) {
        List<String> allColumns = migrationService.getAllColumns();
        List<String> columnsToInsert = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (String column : allColumns) {
            Object value = getValueForColumn(line, column);
            if (value != null) {
                columnsToInsert.add(column);
                params.add(value);
            }
        }

        if (!columnsToInsert.contains("expense_note_id") && line.getExpenseNoteId() != null) {
            columnsToInsert.add("expense_note_id");
            params.add(line.getExpenseNoteId());
        }

        if (!columnsToInsert.isEmpty()) {
            StringBuilder sql = new StringBuilder("INSERT INTO expense_lines (");
            StringBuilder values = new StringBuilder("VALUES (");

            for (int i = 0; i < columnsToInsert.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                    values.append(", ");
                }
                sql.append(columnsToInsert.get(i));
                values.append("?");
            }

            sql.append(") ").append(values).append(")");
            jdbcTemplate.update(sql.toString(), params.toArray());
        }
    }

    private boolean isColumnOfType(String columnName, String targetType) {
        if (!columnTypeCache.containsKey(columnName)) {
            try {
                String sql = """
                    SELECT data_type 
                    FROM information_schema.columns 
                    WHERE table_name = 'expense_lines' 
                    AND column_name = ?
                """;
                String dataType = jdbcTemplate.queryForObject(sql, String.class, columnName);
                columnTypeCache.put(columnName, dataType != null ? dataType.toLowerCase() : "unknown");
            } catch (Exception e) {
                columnTypeCache.put(columnName, "unknown");
            }
        }

        String dataType = columnTypeCache.get(columnName);
        return dataType != null && dataType.contains(targetType.toLowerCase());
    }

    private LocalDate convertToLocalDate(Object value, String columnName) {
        if (value == null) return null;

        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }

        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }

        if (value instanceof String) {
            String str = (String) value;
            str = str.trim();

            if (str.matches("\\d{4}-\\d{2}-\\d{2}")) {
                try {
                    return LocalDate.parse(str);
                } catch (DateTimeParseException e) {
                    System.err.println("❌ Erreur parsing ISO date: " + str);
                }
            } else if (str.matches("\\d{2}/\\d{2}/\\d{4}")) {
                try {
                    String[] parts = str.split("/");
                    return LocalDate.of(
                            Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[0])
                    );
                } catch (Exception e) {
                    System.err.println("❌ Erreur parsing français date: " + str);
                }
            } else if (str.matches("\\d{2}-\\d{2}-\\d{4}")) {
                try {
                    String[] parts = str.split("-");
                    return LocalDate.of(
                            Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[0])
                    );
                } catch (Exception e) {
                    System.err.println("❌ Erreur parsing tirets date: " + str);
                }
            } else {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("[yyyy-MM-dd][dd/MM/yyyy][dd-MM-yyyy]");
                    return LocalDate.parse(str, formatter);
                } catch (Exception e) {
                    System.err.println("❌ Aucun format de date reconnu pour: " + str);
                }
            }
        }

        System.err.println("⚠️ Utilisation date courante pour " + columnName + " (valeur: " + value + ")");
        return LocalDate.now();
    }

    private Object getValueForColumn(ExpenseLine line, String columnName) {
        Object value = switch (columnName) {
            case "expense_note_id" -> line.getExpenseNoteId();
            case "category_id" -> line.getCategoryId();
            case "amount" -> line.getAmount();
            case "expense_date" -> line.getExpenseDate();
            case "description" -> line.getDescription();
            case "justificatif_path" -> line.getJustificatifPath();
            case "depart" -> line.getDepart();
            case "destination" -> line.getDestination();
            case "transport_type" -> line.getTransportType();
            case "nombre_nuits" -> line.getNombreNuits();
            case "hotel_name" -> line.getHotelName();
            case "nombre_personnes" -> line.getNombrePersonnes();
            case "repas_type" -> line.getRepasType();
            case "kilometrage" -> line.getKilometrage();
            case "vehicule" -> line.getVehicule();
            case "detail" -> line.getDetail();
            case "is_anomaly_depense" -> line.getIsAnomalyDepense();
            case "anomaly_expense_message" -> line.getAnomalyExpenseMessage();
            default -> null;
        };

        if (value == null) {
            value = line.getDynamicField(columnName);
            if (value == null) {
                String camelCaseKey = toCamelCase(columnName);
                value = line.getDynamicField(camelCaseKey);
            }
        }

        if (value != null) {
            if (isColumnOfType(columnName, "date")) {
                return convertToLocalDate(value, columnName);
            }
        }

        return value;
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

    private void validateExpenseDate(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException(
                    "Les dépenses ne sont pas autorisées le week-end"
            );
        }

        String formatted = String.format("%02d-%02d",
                date.getDayOfMonth(),
                date.getMonthValue()
        );

        if (TUNISIA_HOLIDAYS.contains(formatted)) {
            throw new IllegalArgumentException(
                    "Les dépenses ne sont pas autorisées pendant les jours fériés"
            );
        }
    }

    public List<ExpenseNote> getNotesByEmployee(String employeeId) {
        return noteRepository.findByEmployeeId(employeeId);
    }

    public List<ExpenseNote> getNotesByStatus(ExpenseStatus status) {
        return noteRepository.findByStatus(status);
    }

    public List<ExpenseNote> getAllNotes() {
        return noteRepository.findAll();
    }

    public List<ExpenseLine> getLines(Long noteId) {
        return lineRepository.findByExpenseNoteId(noteId);
    }

    public ExpenseNote getNoteWithLines(Long noteId) {
        return noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));
    }

    @Transactional
    public void deleteNote(Long noteId, String employeeId) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));

        if (!note.getEmployeeId().equals(employeeId)) {
            throw new RuntimeException("Vous n'êtes pas autorisé à supprimer cette note.");
        }

        if (note.getStatus() != ExpenseStatus.EN_ATTENTE) {
            throw new IllegalStateException("Seules les notes en attente peuvent être supprimées.");
        }

        if (note.getAccordPath() != null) {
            fileStorageService.deleteFile(note.getEmployeeId(), note.getAccordPath());
        }

        List<ExpenseLine> lines = lineRepository.findByExpenseNoteId(noteId);
        for (ExpenseLine line : lines) {
            if (line.getJustificatifPath() != null) {
                fileStorageService.deleteFile(note.getEmployeeId(), line.getJustificatifPath());
            }
        }

        lineRepository.deleteByExpenseNoteId(noteId);
        noteRepository.delete(note);
    }

    @Transactional
    public ExpenseNote updateExpenseNoteWithFiles(
            Long noteId,
            String employeeId,
            ExpenseNote updatedNote,
            List<ExpenseLine> updatedLines,
            MultipartFile newAccordFile,
            List<MultipartFile> newFactureFiles
    ) {
        ExpenseNote existingNote = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));

        if (!existingNote.getEmployeeId().equals(employeeId)) {
            throw new RuntimeException("Vous n'êtes pas autorisé à modifier cette note.");
        }
        if (existingNote.getStatus() != ExpenseStatus.EN_ATTENTE) {
            throw new IllegalStateException("Seules les notes en attente peuvent être modifiées.");
        }

        existingNote.setProjectId(updatedNote.getProjectId());
        existingNote.setUpdatedAt(LocalDateTime.now());

        if (newAccordFile != null && !newAccordFile.isEmpty()) {
            if (existingNote.getAccordPath() != null) {
                fileStorageService.deleteFile(employeeId, existingNote.getAccordPath());
            }
            String newAccordFileName = fileStorageService.storeFile(newAccordFile, employeeId, "accords");
            existingNote.setAccordPath(newAccordFileName);
        }

        List<ExpenseLine> oldLines = lineRepository.findByExpenseNoteId(noteId);
        Map<Long, ExpenseLine> oldLinesMap = oldLines.stream()
                .collect(Collectors.toMap(ExpenseLine::getId, Function.identity()));

        Set<Long> updatedLineIds = updatedLines.stream()
                .map(ExpenseLine::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (ExpenseLine oldLine : oldLines) {
            if (!updatedLineIds.contains(oldLine.getId())) {
                if (oldLine.getJustificatifPath() != null) {
                    fileStorageService.deleteFile(employeeId, oldLine.getJustificatifPath());
                }
                lineRepository.delete(oldLine);
            }
        }

        double total = 0.0;
        for (int i = 0; i < updatedLines.size(); i++) {
            ExpenseLine line = updatedLines.get(i);
            ExpenseLine lineToSave;

            if (line.getId() != null && oldLinesMap.containsKey(line.getId())) {
                lineToSave = oldLinesMap.get(line.getId());
                lineToSave.setCategoryId(line.getCategoryId());
                lineToSave.setAmount(line.getAmount());
                lineToSave.setExpenseDate(line.getExpenseDate());
                lineToSave.setDescription(line.getDescription());
                copyDynamicFields(line, lineToSave);
            } else {
                lineToSave = new ExpenseLine();
                lineToSave.setExpenseNoteId(noteId);
                lineToSave.setCategoryId(line.getCategoryId());
                lineToSave.setAmount(line.getAmount());
                lineToSave.setExpenseDate(line.getExpenseDate());
                lineToSave.setDescription(line.getDescription());
                copyDynamicFields(line, lineToSave);
            }

            MultipartFile fileForThisLine = (newFactureFiles != null && i < newFactureFiles.size()) ? newFactureFiles.get(i) : null;
            if (fileForThisLine != null && !fileForThisLine.isEmpty()) {
                if (lineToSave.getJustificatifPath() != null) {
                    fileStorageService.deleteFile(employeeId, lineToSave.getJustificatifPath());
                }
                String fileName = fileStorageService.storeFile(fileForThisLine, employeeId, "factures");
                lineToSave.setJustificatifPath(fileName);
            }

            if (lineToSave.getExpenseDate() == null) {
                lineToSave.setExpenseDate(LocalDate.now());
            }
            validateExpenseDate(lineToSave.getExpenseDate());

            if (lineToSave.getId() == null) {
                insertExpenseLineWithDynamicColumns(lineToSave);
            } else {
                updateExpenseLineWithDynamicColumns(lineToSave);
            }

            total += lineToSave.getAmount();
        }

        existingNote.setTotalAmount(total);
        return noteRepository.save(existingNote);
    }

    private void updateExpenseLineWithDynamicColumns(ExpenseLine line) {
        List<String> allColumns = migrationService.getAllColumns();
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (String column : allColumns) {
            Object value = getValueForColumn(line, column);
            if (value != null) {
                setClauses.add(column + " = ?");
                params.add(value);
            }
        }

        if (!setClauses.isEmpty()) {
            String sql = "UPDATE expense_lines SET " + String.join(", ", setClauses) + " WHERE id = ?";
            params.add(line.getId());
            jdbcTemplate.update(sql, params.toArray());
        }
    }

    private void copyDynamicFields(ExpenseLine source, ExpenseLine target) {
        target.setDepart(source.getDepart());
        target.setDestination(source.getDestination());
        target.setTransportType(source.getTransportType());
        target.setNombreNuits(source.getNombreNuits());
        target.setHotelName(source.getHotelName());
        target.setNombrePersonnes(source.getNombrePersonnes());
        target.setRepasType(source.getRepasType());
        target.setKilometrage(source.getKilometrage());
        target.setVehicule(source.getVehicule());
        target.setDetail(source.getDetail());

        if (source.getDynamicFields() != null) {
            for (Map.Entry<String, Object> entry : source.getDynamicFields().entrySet()) {
                target.setDynamicField(entry.getKey(), entry.getValue());
            }
        }
    }

    public List<ExpenseNote> getNotesByDepartment(Long departmentId) {
        List<Project> departmentProjects = projectRepository.findByDepartmentId(departmentId);

        if (departmentProjects.isEmpty()) {
            return List.of();
        }

        List<Long> projectIds = departmentProjects.stream()
                .map(Project::getId)
                .collect(Collectors.toList());

        return noteRepository.findByProjectIdIn(projectIds);
    }

    public List<ExpenseNote> getNotesForManager(String managerId, Long departmentId) {
        return getNotesByDepartment(departmentId);
    }

// ========== NOUVELLES MÉTHODES POUR MANAGER ==========

    @Transactional
    public ExpenseNote managerValidateNote(Long noteId, String comment, String managerId, String managerName) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));

        note.setStatus(ExpenseStatus.VALIDEE);
        note.setDecisionComment(comment);
        // ✅ Stocker avec le préfixe "M:" pour les managers
        note.setDecidedBy("M:" + managerName);
        note.setManagerId(managerId);
        note.setDecidedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        return noteRepository.save(note);
    }

    @Transactional
    public ExpenseNote managerRejectNote(Long noteId, String comment, String managerId, String managerName) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));

        note.setStatus(ExpenseStatus.REFUSEE);
        note.setDecisionComment(comment);
        // ✅ Stocker avec le préfixe "M:" pour les managers
        note.setDecidedBy("M:" + managerName);
        note.setManagerId(managerId);
        note.setDecidedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        return noteRepository.save(note);
    }
    // ========== NOUVELLES MÉTHODES POUR ADMIN ==========
// ========== NOUVELLES MÉTHODES POUR ADMIN ==========

    @Transactional
    public ExpenseNote adminRejectNote(Long noteId, String comment) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));

        if (note.getStatus() != ExpenseStatus.VALIDEE) {
            throw new IllegalStateException("Seules les notes validées peuvent être refusées par l'admin");
        }

        note.setStatus(ExpenseStatus.REFUSEE);
        note.setDecisionComment(comment);
        note.setDecidedBy("Admin");  // ✅ Garder "Admin" sans préfixe
        note.setDecidedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        return noteRepository.save(note);
    }

    @Transactional
    public ExpenseNote adminReimburseNote(Long noteId, String comment) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));

        if (note.getStatus() != ExpenseStatus.VALIDEE) {
            throw new IllegalStateException("Seules les notes validées peuvent être remboursées");
        }

        note.setStatus(ExpenseStatus.REMBOURSEE);
        if (comment != null && !comment.trim().isEmpty()) {
            note.setDecisionComment(comment);
        }
        note.setDecidedBy("Admin");  // ✅ Garder "Admin" sans préfixe
        note.setDecidedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        return noteRepository.save(note);
    }
    // ========== MÉTHODES EXISTANTES CONSERVÉES POUR COMPATIBILITÉ ==========

    @Transactional
    public ExpenseNote validateNote(Long noteId) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));
        note.setUpdatedAt(LocalDateTime.now());
        note.setStatus(ExpenseStatus.VALIDEE);
        return noteRepository.save(note);
    }

    @Transactional
    public ExpenseNote validateNote(Long noteId, String comment) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));

        note.setUpdatedAt(LocalDateTime.now());
        note.setStatus(ExpenseStatus.VALIDEE);

        if (comment != null && !comment.trim().isEmpty()) {
            note.setDecisionComment(comment);
            note.setDecidedBy("Manager (Legacy)");
            note.setDecidedAt(LocalDateTime.now());
        }

        return noteRepository.save(note);
    }

    // Dans ExpenseService.java - Méthode pour le manager (ancien endpoint)
    @Transactional
    public ExpenseNote refuseNote(Long noteId, String comment) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));

        note.setStatus(ExpenseStatus.REFUSEE);
        note.setDecisionComment(comment);
        note.setDecidedBy("M:Manager");  // ✅ Changé : "Manager" au lieu de "Admin (Legacy)"
        note.setDecidedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        return noteRepository.save(note);
    }


//=========================== ANOMALY DETECTION ================================


}