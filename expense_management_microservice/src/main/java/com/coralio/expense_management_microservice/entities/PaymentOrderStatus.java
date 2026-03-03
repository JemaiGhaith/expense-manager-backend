package com.coralio.expense_management_microservice.entities;

public enum PaymentOrderStatus {
    EN_ATTENTE("En attente de paiement"),
    PAYE("Payé"),
    ANNULE("Annulé");

    private final String displayName;

    PaymentOrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}