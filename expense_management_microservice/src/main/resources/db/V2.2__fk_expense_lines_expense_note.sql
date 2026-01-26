-- Foreign keys locales
ALTER TABLE expense_lines
    ADD CONSTRAINT fk_expense_note
        FOREIGN KEY (expense_note_id) REFERENCES expense_notes(id);
