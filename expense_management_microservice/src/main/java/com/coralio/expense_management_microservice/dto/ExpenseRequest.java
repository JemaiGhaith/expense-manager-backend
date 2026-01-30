package com.coralio.expense_management_microservice.dto;


import com.coralio.expense_management_microservice.entities.ExpenseLine;
import com.coralio.expense_management_microservice.entities.ExpenseNote;

import java.util.List;

public class ExpenseRequest {
    private ExpenseNote note;
    private List<ExpenseLine> lines;

    // Getters & Setters
    public ExpenseNote getNote() { return note; }
    public void setNote(ExpenseNote note) { this.note = note; }
    public List<ExpenseLine> getLines() { return lines; }
    public void setLines(List<ExpenseLine> lines) { this.lines = lines; }
}
