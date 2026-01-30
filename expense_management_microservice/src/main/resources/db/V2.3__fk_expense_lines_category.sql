-- Foreign keys locales
ALTER TABLE expense_lines
    ADD CONSTRAINT fk_category
        FOREIGN KEY (category_id) REFERENCES expense_categories(id);
