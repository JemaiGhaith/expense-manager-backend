package com.coralio.expense_management_microservice.enums;

public enum ProjectStatus {
    ACTIF("Actif"),
    INACTIF("Inactif"),
    CLOTURE("Clôturé");

    private final String label;

    ProjectStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ProjectStatus fromString(String text) {
        for (ProjectStatus status : ProjectStatus.values()) {
            if (status.label.equalsIgnoreCase(text) || status.name().equalsIgnoreCase(text)) {
                return status;
            }
        }
        return ACTIF;
    }
}