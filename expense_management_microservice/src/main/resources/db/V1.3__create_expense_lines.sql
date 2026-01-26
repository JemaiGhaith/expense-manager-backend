-- Lignes de dépense
CREATE TABLE expense_lines (
    id SERIAL PRIMARY KEY,
    expense_note_id BIGINT NOT NULL,
    category_id BIGINT,
    amount NUMERIC(12,2) NOT NULL,
    expense_date DATE NOT NULL,
    description TEXT
);
