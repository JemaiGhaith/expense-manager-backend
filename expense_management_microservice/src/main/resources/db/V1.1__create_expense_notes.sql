-- Notes de frais
CREATE TABLE expense_notes (
    id SERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL, -- FK vers auth_users si nécessaire
    project_id BIGINT,
    status VARCHAR(20) DEFAULT 'EN_ATTENTE', -- EN_ATTENTE, VALIDEE, REFUSEE, REMBOURSEE
    total_amount NUMERIC(12,2) DEFAULT 0.0,
    manager_comment TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP
);
