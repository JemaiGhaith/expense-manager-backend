package com.coralio.expense_management_microservice.enums;

public enum DepartmentStatus {
    ACTIF("Actif"),
    INACTIF("Inactif");

    private final String label;

    DepartmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static DepartmentStatus fromString(String text) {
        for (DepartmentStatus status : DepartmentStatus.values()) {
            if (status.label.equalsIgnoreCase(text) || status.name().equalsIgnoreCase(text)) {
                return status;
            }
        }
        return ACTIF;
    }
}