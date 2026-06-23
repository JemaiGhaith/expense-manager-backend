package com.coralio.notification_microservice.enums;

public enum NotificationType {
    EXPENSE_CREATED,
    EXPENSE_APPROVED,
    EXPENSE_REJECTED,
    EXPENSE_REIMBURSED,
    EXPENSE_PENDING,
    DOCUMENTS_MISSING,
    DOCUMENTS_UPLOADED,
    PAYMENT_PROCESSED,
    USER_WELCOME,
    PASSWORD_CHANGED,
    SYSTEM_ALERT,
    // NOUVEAUX TYPES
    PENDING_APPROVAL,           // Note en attente pour manager
    CATEGORY_LIMIT_EXCEEDED,    // Dépassement plafond catégorie
    BUDGET_LIMIT_EXCEEDED,      // Dépassement budget projet
    NOTES_READY_FOR_REIMBURSEMENT, // Notes validées prêtes pour remboursement (Admin)
    EXPENSE_VALIDATED_BY_MANAGER,  // ✅ NOUVEAU : Pour admin
    EXPENSE_VALIDATED_BY_ADMIN,  // <-- new
    INTERNAL_NOTE_ADDED   // ← AJOUTER CETTE LIGNE
}
