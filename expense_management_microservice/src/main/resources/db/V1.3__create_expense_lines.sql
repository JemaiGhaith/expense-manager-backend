-- Lignes de dépense
CREATE TABLE expense_lines (
                               id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,

                               expense_note_id BIGINT NOT NULL,
                               category_id BIGINT NOT NULL,

                               amount DOUBLE PRECISION NOT NULL,

                               expense_date DATE NOT NULL,
                               description VARCHAR(500),

                               justificatif_path VARCHAR(255),

    -- =====================
    -- TRANSPORT
    -- =====================
                               depart VARCHAR(100),
                               destination VARCHAR(100),
                               transport_type VARCHAR(50),

    -- =====================
    -- HÉBERGEMENT
    -- =====================
                               nombre_nuits INT,
                               hotel_name VARCHAR(150),

    -- =====================
    -- RESTAURATION
    -- =====================
                               nombre_personnes INT,
                               repas_type VARCHAR(50),

    -- =====================
    -- CARBURANT
    -- =====================
                               kilometrage DOUBLE PRECISION,
                               vehicule VARCHAR(100),

    -- =====================
    -- DIVERS
    -- =====================
                               detail VARCHAR(500)
);
