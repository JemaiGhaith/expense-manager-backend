-- Foreign keys locales
ALTER TABLE expense_notes
    ADD CONSTRAINT fk_project
        FOREIGN KEY (project_id) REFERENCES expense_projects(id);
