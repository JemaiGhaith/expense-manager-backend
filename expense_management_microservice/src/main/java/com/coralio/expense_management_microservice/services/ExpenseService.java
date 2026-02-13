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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        return createExpenseNoteWithFiles(note, lines, null);
    }

    // ✅ CORRIGÉ - Sauvegarde dynamique avec toutes les colonnes
    @Transactional
    public ExpenseNote createExpenseNoteWithFiles(
            ExpenseNote note,
            List<ExpenseLine> lines,
            List<String> fileNames
    ) {
        // 1️⃣ Initialiser la note
        if (note.getStatus() == null) {
            note.setStatus(ExpenseStatus.EN_ATTENTE);
        }
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        // 2️⃣ Valider les dates
        for (ExpenseLine line : lines) {
            if (line.getExpenseDate() == null) {
                line.setExpenseDate(LocalDate.now());
            }
            validateExpenseDate(line.getExpenseDate());
        }

        // 3️⃣ Sauvegarder la note
        ExpenseNote savedNote = noteRepository.save(note);

        // 4️⃣ ✅ Sauvegarder chaque ligne avec INSERT dynamique
        for (int i = 0; i < lines.size(); i++) {
            ExpenseLine line = lines.get(i);
            line.setExpenseNoteId(savedNote.getId());

            if (fileNames != null && i < fileNames.size()) {
                line.setJustificatifPath(fileNames.get(i));
            }

            // ✅ Utiliser JDBC pour un INSERT dynamique
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

    // ✅ INSERT dynamique avec toutes les colonnes de la table
    // ✅ CORRIGÉ - INSERT dynamique avec SEULEMENT les colonnes qui ont des valeurs
    private void insertExpenseLineWithDynamicColumns(ExpenseLine line) {
        // 1️⃣ Récupérer toutes les colonnes de la table
        List<String> allColumns = migrationService.getAllColumns();

        // 2️⃣ Filtrer UNIQUEMENT les colonnes qui ont des valeurs
        List<String> columnsToInsert = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (String column : allColumns) {
            Object value = getValueForColumn(line, column);
            if (value != null) {
                columnsToInsert.add(column);
                params.add(value);
            }
        }

        // 3️⃣ TOUJOURS inclure expense_note_id même si null ? Non, on vérifie
        // Si expense_note_id n'est pas dans la liste, on l'ajoute
        if (!columnsToInsert.contains("expense_note_id") && line.getExpenseNoteId() != null) {
            columnsToInsert.add("expense_n_id"); // ✅ CORRECTION: le nom exact de la colonne
            params.add(line.getExpenseNoteId());
        }

        // 4️⃣ Construire la requête INSERT
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

        sql.append(") ");
        values.append(")");
        sql.append(values);

        // 5️⃣ Exécuter l'insertion
        if (!columnsToInsert.isEmpty()) {
            jdbcTemplate.update(sql.toString(), params.toArray());
            System.out.println("✅ Insertion avec colonnes: " + columnsToInsert);
        }
    }
    // ✅ CORRIGÉ - Mapper les valeurs de l'entité aux colonnes
    private Object getValueForColumn(ExpenseLine line, String columnName) {
        // 1️⃣ Essayer les champs standards
        Object value = switch (columnName) {
            case "expense_note_id", "expense_n_id" -> line.getExpenseNoteId();  // ✅ Support les deux noms
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
            // Essayer avec le nom exact de la colonne
            value = line.getDynamicField(columnName);

            // Si toujours null, essayer de convertir le camelCase en snake_case
            if (value == null) {
                // Essayer de trouver une correspondance dans dynamicFields
                for (Map.Entry<String, Object> entry : line.getDynamicFields().entrySet()) {
                    String dynamicKey = entry.getKey();
                    // Convertir le nom de colonne (snake_case) en camelCase pour la recherche
                    String camelCaseKey = toCamelCase(columnName);
                    if (dynamicKey.equals(columnName) ||
                            dynamicKey.equals(camelCaseKey) ||
                            dynamicKey.equalsIgnoreCase(columnName)) {
                        value = entry.getValue();
                        break;
                    }
                }
            }
        }

        // 3️⃣ DEBUG - Afficher les colonnes avec leurs valeurs
        if (value != null) {
            System.out.println("📌 Colonne: " + columnName + " = " + value + " (type: " + value.getClass().getSimpleName() + ")");
        }

        return value;
    }

    // ✅ Convertir snake_case en camelCase
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
    // ✅ VALIDATION DES DATES
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

    // ==================== MÉTHODES DE LECTURE ====================

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
}