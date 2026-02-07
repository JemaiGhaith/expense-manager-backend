package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.entities.ExpenseLine;
import com.coralio.expense_management_microservice.entities.ExpenseNote;
import com.coralio.expense_management_microservice.entities.ExpenseStatus;
import com.coralio.expense_management_microservice.repos.ExpenseLineRepository;
import com.coralio.expense_management_microservice.repos.ExpenseNoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
public class ExpenseService {

    private final ExpenseNoteRepository noteRepository;
    private final ExpenseLineRepository lineRepository;


    private static final Set<String> TUNISIA_HOLIDAYS = Set.of(
            "01-01",
            "14-01",
            "20-03",
            "09-04",
            "01-05",
            "25-07",
            "13-08",
            "15-10",
            "17-12"
    );

    @Autowired
    private FileStorageService fileStorageService;

    public ExpenseService(ExpenseNoteRepository noteRepository, ExpenseLineRepository lineRepository) {
        this.noteRepository = noteRepository;
        this.lineRepository = lineRepository;
    }

    // Créer une note avec ses lignes (sans fichiers)
    public ExpenseNote createExpenseNote(ExpenseNote note, List<ExpenseLine> lines) {
        return createExpenseNoteWithFiles(note, lines, null);
    }

    // Créer une note avec fichiers
    public ExpenseNote createExpenseNoteWithFiles(
            ExpenseNote note,
            List<ExpenseLine> lines,
            List<String> fileNames
    ) {

        if (note.getStatus() == null) {
            note.setStatus(ExpenseStatus.EN_ATTENTE);
        }

        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        // 🔴 VALIDATION DES DATES AVANT TOUTE SAUVEGARDE
        for (ExpenseLine line : lines) {

            if (line.getExpenseDate() == null) {
                line.setExpenseDate(LocalDate.now());
            }


        }

        ExpenseNote savedNote = noteRepository.save(note);

        for (int i = 0; i < lines.size(); i++) {
            ExpenseLine line = lines.get(i);

            line.setExpenseNoteId(savedNote.getId());

            if (fileNames != null && i < fileNames.size()) {
                line.setJustificatifPath(fileNames.get(i));
            }
        }

        lineRepository.saveAll(lines);

        double total = lines.stream()
                .mapToDouble(ExpenseLine::getAmount)
                .sum();

        savedNote.setTotalAmount(total);
        savedNote.setUpdatedAt(LocalDateTime.now());

        return noteRepository.save(savedNote);
    }


    // Récupérer toutes les notes d'un employé
    public List<ExpenseNote> getNotesByEmployee(String employeeId) {
        return noteRepository.findByEmployeeId(employeeId);
    }

    // Récupérer toutes les notes par status
    public List<ExpenseNote> getNotesByStatus(ExpenseStatus status) {
        return noteRepository.findByStatus(status);
    }

    // Valider une note (manager)
    public ExpenseNote validateNote(Long noteId) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));
        note.setUpdatedAt(LocalDateTime.now());
        note.setStatus(ExpenseStatus.VALIDEE);
        return noteRepository.save(note);
    }

    // Récupérer toutes les notes
    public List<ExpenseNote> getAllNotes() {
        return noteRepository.findAll();
    }

    // Refuser une note (manager)
    public ExpenseNote refuseNote(Long noteId, String comment) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));
        note.setStatus(ExpenseStatus.REFUSEE);
        note.setManagerComment(comment);
        note.setUpdatedAt(LocalDateTime.now());
        return noteRepository.save(note);
    }

    // Récupérer les lignes d'une note
    public List<ExpenseLine> getLines(Long noteId) {
        return lineRepository.findByExpenseNoteId(noteId);
    }

    // Récupérer une note par ID avec ses lignes
    public ExpenseNote getNoteWithLines(Long noteId) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note non trouvée avec l'ID: " + noteId));

        List<ExpenseLine> lines = lineRepository.findByExpenseNoteId(noteId);
        // Note: Normalement vous devriez avoir une relation @OneToMany dans l'entité
        // ou créer un DTO pour retourner note + lines
        return note;
    }



    private void validateExpenseDate(LocalDate date) {

        // 1️⃣ Week-end
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException(
                    "Les dépenses ne sont pas autorisées le week-end"
            );
        }

        // 2️⃣ Jour férié
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





}