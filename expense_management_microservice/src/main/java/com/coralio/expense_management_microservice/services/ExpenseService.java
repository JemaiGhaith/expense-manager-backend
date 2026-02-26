package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.entities.ExpenseLine;
import com.coralio.expense_management_microservice.entities.ExpenseNote;
import com.coralio.expense_management_microservice.entities.ExpenseStatus;
import com.coralio.expense_management_microservice.repos.ExpenseLineRepository;
import com.coralio.expense_management_microservice.repos.ExpenseNoteRepository;
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
@Service
public class ExpenseService {

    private final ExpenseNoteRepository noteRepository;
    private final ExpenseLineRepository lineRepository;
    private final DatabaseMigrationService migrationService;
    private final JdbcTemplate jdbcTemplate;

    private static final Set<String> TUNISIA_HOLIDAYS = Set.of(
            "01-01", "14-01", "20-03", "09-04", "01-05",
            "25-07", "13-08", "15-10", "17-12"
    );

    // ✅ Cache pour les types de colonnes
    private final Map<String, String> columnTypeCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    private FileStorageService fileStorageService;

    public ExpenseService(
            ExpenseNoteRepository noteRepository,
            ExpenseLineRepository lineRepository,
            DatabaseMigrationService migrationService,
            JdbcTemplate jdbcTemplate) {
        this.noteRepository = noteRepository;
        this.lineRepository = lineRepository;
        this.migrationService = migrationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // Créer une note avec ses lignes (sans fichiers)
    @Transactional
    public ExpenseNote createExpenseNote(ExpenseNote note, List<ExpenseLine> lines) {
        return createExpenseNoteWithFiles(note, lines, null, null);
    }

    // ✅ Nouvelle méthode avec accord et factures
    @Transactional
    public ExpenseNote createExpenseNoteWithFiles(
            ExpenseNote note,
            List<ExpenseLine> lines,
            String accordFileName,
            List<String> factureFileNames
    ) {
        // 1️⃣ Initialiser la note
        if (note.getStatus() == null) {
            note.setStatus(ExpenseStatus.EN_ATTENTE);
        }
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        // ✅ Sauvegarder le chemin de l'accord
        if (accordFileName != null) {
            note.setAccordPath(accordFileName);
        }

        // 2️⃣ Valider les dates
        for (ExpenseLine line : lines) {
            if (line.getExpenseDate() == null) {
                line.setExpenseDate(LocalDate.now());
            }
            validateExpenseDate(line.getExpenseDate());
        }

        // 3️⃣ Sauvegarder la note
        ExpenseNote savedNote = noteRepository.save(note);

        // 4️⃣ Sauvegarder chaque ligne avec sa facture
        for (int i = 0; i < lines.size(); i++) {
            ExpenseLine line = lines.get(i);
            line.setExpenseNoteId(savedNote.getId());

            if (factureFileNames != null && i < factureFileNames.size()) {
                line.setJustificatifPath(factureFileNames.get(i));
            }

            insertExpenseLineWithDynamicColumns(line);
        }

        // 5️⃣ Calculer le total
        double total = lines.stream()
                .mapToDouble(ExpenseLine::getAmount)
                .sum();

        savedNote.setTotalAmount(total);
        savedNote.setUpdatedAt(LocalDateTime.now());

        return noteRepository.save(savedNote);
    }

    // Garder l'ancienne méthode pour compatibilité
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

                // 🔍 DEBUG pour les dates
                if (isColumnOfType(column, "date")) {
                    System.out.println("📅 DATE préparée: " + column + " = " + value + " (" + value.getClass().getSimpleName() + ")");
                }
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

            // 🔍 DEBUG - Voir la requête complète
            System.out.println("📝 SQL: " + sql.toString());
            System.out.println("📦 Paramètres: " + params);

            jdbcTemplate.update(sql.toString(), params.toArray());
            System.out.println("✅ Insertion avec colonnes: " + columnsToInsert);
        }
    }

    /**
     * ✅ Vérifie si une colonne est d'un certain type dans la base
     */
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

    /**
     * ✅ Convertit une valeur en LocalDate pour les colonnes DATE
     */
    private LocalDate convertToLocalDate(Object value, String columnName) {
        if (value == null) return null;

        // ✅ Déjà LocalDate
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }

        // ✅ Déjà java.sql.Date
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }

        // ✅ String - essayer différents formats
        if (value instanceof String) {
            String str = (String) value;
            str = str.trim();

            // Format ISO (2024-01-15)
            if (str.matches("\\d{4}-\\d{2}-\\d{2}")) {
                try {
                    return LocalDate.parse(str);
                } catch (DateTimeParseException e) {
                    System.err.println("❌ Erreur parsing ISO date: " + str);
                }
            }

            // Format français (15/01/2024)
            else if (str.matches("\\d{2}/\\d{2}/\\d{4}")) {
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
            }

            // Format avec tirets (15-01-2024)
            else if (str.matches("\\d{2}-\\d{2}-\\d{4}")) {
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
            }

            // Essayer avec DateTimeFormatter
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("[yyyy-MM-dd][dd/MM/yyyy][dd-MM-yyyy]");
                return LocalDate.parse(str, formatter);
            } catch (Exception e) {
                System.err.println("❌ Aucun format de date reconnu pour: " + str);
            }
        }

        // ⚠️ Fallback: date du jour
        System.err.println("⚠️ Utilisation date courante pour " + columnName + " (valeur: " + value + ")");
        return LocalDate.now();
    }

    /**
     * ✅ Version corrigée de getValueForColumn avec gestion des dates
     */
    private Object getValueForColumn(ExpenseLine line, String columnName) {
        // 1️⃣ Essayer les champs standards
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
            default -> null;
        };

        // 2️⃣ Si c'est null, essayer les champs dynamiques
        if (value == null) {
            value = line.getDynamicField(columnName);

            if (value == null) {
                String camelCaseKey = toCamelCase(columnName);
                value = line.getDynamicField(camelCaseKey);
            }
        }

        // 3️⃣ 🔥 CORRECTION CRITIQUE : Convertir les dates !
        if (value != null) {
            // Vérifier si c'est une colonne DATE
            if (isColumnOfType(columnName, "date")) {
                LocalDate dateValue = convertToLocalDate(value, columnName);
                System.out.println("📅 Conversion date pour " + columnName +
                        ": " + value + " (" + value.getClass().getSimpleName() +
                        ") → " + dateValue + " (LocalDate)");
                return dateValue;
            }

            // DEBUG
            System.out.println("📌 Colonne: " + columnName + " = " + value +
                    " (type: " + value.getClass().getSimpleName() + ")");
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

    @Transactional
    public ExpenseNote validateNote(Long noteId) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));
        note.setUpdatedAt(LocalDateTime.now());
        note.setStatus(ExpenseStatus.VALIDEE);
        return noteRepository.save(note);
    }
    /**
     * ✅ Valide une note avec commentaire optionnel
     */
    @Transactional
    public ExpenseNote validateNote(Long noteId, String comment) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));

        note.setUpdatedAt(LocalDateTime.now());
        note.setStatus(ExpenseStatus.VALIDEE);

        // ✅ Ajouter le commentaire s'il est fourni
        if (comment != null && !comment.trim().isEmpty()) {
            note.setManagerComment(comment);
        }

        return noteRepository.save(note);
    }
    public List<ExpenseNote> getAllNotes() {
        return noteRepository.findAll();
    }

    @Transactional
    public ExpenseNote refuseNote(Long noteId, String comment) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));
        note.setStatus(ExpenseStatus.REFUSEE);
        note.setManagerComment(comment);
        note.setUpdatedAt(LocalDateTime.now());
        return noteRepository.save(note);
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

        // Vérifier que la note appartient à l'employé
        if (!note.getEmployeeId().equals(employeeId)) {
            throw new RuntimeException("Vous n'êtes pas autorisé à supprimer cette note.");
        }

        // Vérifier que la note est en attente
        if (note.getStatus() != ExpenseStatus.EN_ATTENTE) {
            throw new IllegalStateException("Seules les notes en attente peuvent être supprimées.");
        }

        // Supprimer le fichier d'accord s'il existe
        if (note.getAccordPath() != null) {
            fileStorageService.deleteFile(note.getEmployeeId(), note.getAccordPath());
        }

        // Récupérer les lignes pour supprimer leurs justificatifs
        List<ExpenseLine> lines = lineRepository.findByExpenseNoteId(noteId);
        for (ExpenseLine line : lines) {
            if (line.getJustificatifPath() != null) {
                fileStorageService.deleteFile(note.getEmployeeId(), line.getJustificatifPath());
            }
        }

        // Supprimer les lignes (via une méthode dédiée dans le repository)
        lineRepository.deleteByExpenseNoteId(noteId);

        // Supprimer la note
        noteRepository.delete(note);
    }
    @Transactional
    public ExpenseNote updateExpenseNoteWithFiles(
            Long noteId,
            String employeeId,
            ExpenseNote updatedNote,
            List<ExpenseLine> updatedLines,
            MultipartFile newAccordFile,
            List<MultipartFile> newFactureFiles  // Liste ordonnée, peut contenir des null
    ) {
        ExpenseNote existingNote = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));

        // Vérifications
        if (!existingNote.getEmployeeId().equals(employeeId)) {
            throw new RuntimeException("Vous n'êtes pas autorisé à modifier cette note.");
        }
        if (existingNote.getStatus() != ExpenseStatus.EN_ATTENTE) {
            throw new IllegalStateException("Seules les notes en attente peuvent être modifiées.");
        }

        // Mise à jour des champs simples
        existingNote.setProjectId(updatedNote.getProjectId());
        existingNote.setUpdatedAt(LocalDateTime.now());

        // Gestion de l'accord
        if (newAccordFile != null && !newAccordFile.isEmpty()) {
            if (existingNote.getAccordPath() != null) {
                fileStorageService.deleteFile(employeeId, existingNote.getAccordPath());
            }
            String newAccordFileName = fileStorageService.storeFile(newAccordFile, employeeId, "accords");
            existingNote.setAccordPath(newAccordFileName);
        }

        // Récupérer les anciennes lignes
        List<ExpenseLine> oldLines = lineRepository.findByExpenseNoteId(noteId);
        Map<Long, ExpenseLine> oldLinesMap = oldLines.stream()
                .collect(Collectors.toMap(ExpenseLine::getId, Function.identity()));

        Set<Long> updatedLineIds = updatedLines.stream()
                .map(ExpenseLine::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Supprimer les lignes qui ne sont plus dans la nouvelle liste
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
                // Ligne existante : mise à jour
                lineToSave = oldLinesMap.get(line.getId());
                lineToSave.setCategoryId(line.getCategoryId());
                lineToSave.setAmount(line.getAmount());
                lineToSave.setExpenseDate(line.getExpenseDate());
                lineToSave.setDescription(line.getDescription());
                copyDynamicFields(line, lineToSave);
            } else {
                // Nouvelle ligne
                lineToSave = new ExpenseLine();
                lineToSave.setExpenseNoteId(noteId);
                lineToSave.setCategoryId(line.getCategoryId());
                lineToSave.setAmount(line.getAmount());
                lineToSave.setExpenseDate(line.getExpenseDate());
                lineToSave.setDescription(line.getDescription());
                copyDynamicFields(line, lineToSave);
            }

            // Gestion du justificatif
            MultipartFile fileForThisLine = (newFactureFiles != null && i < newFactureFiles.size()) ? newFactureFiles.get(i) : null;
            if (fileForThisLine != null && !fileForThisLine.isEmpty()) {
                // Nouveau fichier fourni
                if (lineToSave.getJustificatifPath() != null) {
                    fileStorageService.deleteFile(employeeId, lineToSave.getJustificatifPath());
                }
                String fileName = fileStorageService.storeFile(fileForThisLine, employeeId, "factures");
                lineToSave.setJustificatifPath(fileName);
            } // sinon on garde l'ancien chemin

            // Validation de la date
            if (lineToSave.getExpenseDate() == null) {
                lineToSave.setExpenseDate(LocalDate.now());
            }
            validateExpenseDate(lineToSave.getExpenseDate());

            // Sauvegarde
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
            System.out.println("✅ Mise à jour ligne " + line.getId());
        }
    }
    private void copyDynamicFields(ExpenseLine source, ExpenseLine target) {
        // Copie des champs standards
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

        // Copie des champs dynamiques (via la Map)
        if (source.getDynamicFields() != null) {
            for (Map.Entry<String, Object> entry : source.getDynamicFields().entrySet()) {
                target.setDynamicField(entry.getKey(), entry.getValue());
            }
        }
    }
}