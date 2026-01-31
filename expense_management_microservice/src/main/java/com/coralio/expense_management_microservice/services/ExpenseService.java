package com.coralio.expense_management_microservice.services;
import com.coralio.expense_management_microservice.entities.ExpenseLine;
import com.coralio.expense_management_microservice.entities.ExpenseNote;
import com.coralio.expense_management_microservice.entities.ExpenseStatus;
import com.coralio.expense_management_microservice.repos.ExpenseLineRepository;
import com.coralio.expense_management_microservice.repos.ExpenseNoteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExpenseService {

    private final ExpenseNoteRepository noteRepository;
    private final ExpenseLineRepository lineRepository;

    public ExpenseService(ExpenseNoteRepository noteRepository, ExpenseLineRepository lineRepository) {
        this.noteRepository = noteRepository;
        this.lineRepository = lineRepository;
    }

    // Créer une note avec ses lignes
    public ExpenseNote createExpenseNote(ExpenseNote note, List<ExpenseLine> lines) {

        // 🚀 Forcer les valeurs par défaut
        if (note.getStatus() == null) {
            note.setStatus(ExpenseStatus.EN_ATTENTE);
        }
        if (note.getCreatedAt() == null) {
            note.setCreatedAt(LocalDateTime.now());
        }
        note.setUpdatedAt(LocalDateTime.now());

        // Sauvegarde de la note
        ExpenseNote savedNote = noteRepository.save(note);

        // Lier les lignes à la note
        lines.forEach(line -> line.setExpenseNoteId(savedNote.getId()));
        lineRepository.saveAll(lines);

        // Calcul du total
        double total = lines.stream().mapToDouble(ExpenseLine::getAmount).sum();
        savedNote.setTotalAmount(total);

        // Sauvegarde finale avec total et updatedAt
        savedNote.setUpdatedAt(LocalDateTime.now());
        return noteRepository.save(savedNote);
    }


    // Récupérer toutes les notes d’un employé
    public List<ExpenseNote> getNotesByEmployee(String employeeId) {
        return noteRepository.findByEmployeeId(employeeId);
    }
    // Récupérer toutes les notes par status
    public List<ExpenseNote> getNotesByStatus(ExpenseStatus status) {
        return noteRepository.findByStatus(status);
    }

    // Valider une note (manager)
    public ExpenseNote validateNote(Long noteId) {
        ExpenseNote note = noteRepository.findById(noteId).orElseThrow();
        note.setUpdatedAt(LocalDateTime.now()); // <-- met à jour la date
        note.setStatus(ExpenseStatus.VALIDEE);
        return noteRepository.save(note);
    }

    public List<ExpenseNote> getAllNotes() {
        return noteRepository.findAll();
    }

    // Refuser une note (manager)
    public ExpenseNote refuseNote(Long noteId, String comment) {
        ExpenseNote note = noteRepository.findById(noteId).orElseThrow();
        note.setStatus(ExpenseStatus.REFUSEE);
        note.setManagerComment(comment);
        note.setUpdatedAt(LocalDateTime.now()); // <-- met à jour la date
        return noteRepository.save(note);
    }

    // Récupérer les lignes d’une note
    public List<ExpenseLine> getLines(Long noteId) {
        return lineRepository.findByExpenseNoteId(noteId);
    }
}
