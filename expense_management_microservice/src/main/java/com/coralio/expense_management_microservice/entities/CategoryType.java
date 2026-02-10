package com.coralio.expense_management_microservice.entities;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

public enum CategoryType {
    TRANSPORT,
    HEBERGEMENT,
    RESTAURATION,
    CARBURANT,
    FRAIS_DIVERS,
    AUTRE
}