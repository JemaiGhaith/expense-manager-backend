/* =====================================================
   SCRIPT D'INITIALISATION - EXPENSE MANAGEMENT
   ===================================================== */

/* =========================
   CATEGORIES
   ========================= */
INSERT INTO expense_categories (name, plafond) VALUES
                                                   ('Transport', 150.0),
                                                   ('Hébergement', 500.0),
                                                   ('Restauration', 200.0),
                                                   ('Fournitures', 300.0),
                                                   ('Communication', 100.0);

/* =========================
   PROJECTS
   ========================= */
INSERT INTO expense_projects (name, code) VALUES
                                              ('Projet Alpha', 'ALPHA-001'),
                                              ('Projet Beta', 'BETA-002'),
                                              ('Projet Gamma', 'GAMMA-003');

/* =========================
   EXPENSE NOTES
   ========================= */
INSERT INTO expense_notes (
    employee_id,
    project_id,
    status,
    total_amount,
    manager_comment,
    created_at,
    updated_at
) VALUES
      (
          'emp-001',
          1,
          'EN_ATTENTE',
          120.0,
          NULL,
          CURRENT_TIMESTAMP,
          NULL
      ),
      (
          'emp-002',
          2,
          'VALIDEE',
          350.0,
          'RAS',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      ),
      (
          'emp-001',
          3,
          'REFUSEE',
          80.0,
          'Montant dépasse le plafond',
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
      );

/* =========================
   EXPENSE LINES
   ========================= */
INSERT INTO expense_lines (
    expense_note_id,
    category_id,
    amount,
    expense_date,
    description,
    justificatif_path
) VALUES
      (
          1,
          1,
          50.0,
          '2025-01-10',
          'Taxi aéroport',
          'emp-001/taxi.pdf'
      ),
      (
          1,
          3,
          70.0,
          '2025-01-10',
          'Déjeuner client',
          'emp-001/dejeuner.jpg'
      ),
      (
          2,
          2,
          300.0,
          '2025-01-05',
          'Hôtel mission',
          'emp-002/hotel.pdf'
      ),
      (
          3,
          5,
          80.0,
          '2025-01-08',
          'Carte SIM internationale',
          'emp-001/sim.png'
      );

/* =====================================================
   FIN DU SCRIPT
   ===================================================== */
