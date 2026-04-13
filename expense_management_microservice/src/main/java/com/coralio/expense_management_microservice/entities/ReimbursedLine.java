package com.coralio.expense_management_microservice.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reimbursed_lines")
@Data
public class ReimbursedLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_line_id", nullable = false)
    private ExpenseLine expenseLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id", nullable = false)
    private PaymentOrder paymentOrder;

    @Column(nullable = false)
    private Double originalAmount;

    @Column(nullable = false)
    private Double reimbursedAmount;

    @Column(nullable = false)
    private Boolean isFullyReimbursed;

    @Column(length = 500)
    private String adminComment;

    @CreationTimestamp
    private LocalDateTime createdAt;
    // ✅ NOUVEAUX CHAMPS POUR DEVISE
    @Column(name = "reimbursed_amount_display")
    private Double reimbursedAmountDisplay;  // Montant dans la devise d'affichage

    // Méthode pour vérifier si le remboursement respecte les règles
    public boolean isValidReimbursement(Double categoryCeiling) {
        // Règle: ne pas dépasser le montant original
        if (reimbursedAmount > originalAmount) {
            return false;
        }

        // Règle pour remboursement partiel: limité par le plafond
        if (!isFullyReimbursed && reimbursedAmount < originalAmount) {
            double maxPartial = Math.min(originalAmount, categoryCeiling);
            return reimbursedAmount <= maxPartial;
        }

        return true;
    }
}