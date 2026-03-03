package com.coralio.expense_management_microservice.entities;

public enum PaymentMethod {
    VIREMENT("Virement bancaire"),
    ESPECES("Espèces"),
    CHEQUE("Chèque");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}