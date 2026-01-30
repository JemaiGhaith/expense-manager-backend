-- Catégories
CREATE TABLE expense_categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    plafond NUMERIC(12,2)
);
