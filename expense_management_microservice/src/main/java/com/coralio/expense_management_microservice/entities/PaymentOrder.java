package com.coralio.expense_management_microservice.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payment_orders")
@Data
@ToString(exclude = {"reimbursedLines"})
public class PaymentOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_note_id", nullable = false, unique = true)
    private ExpenseNote expenseNote;

    @Column(nullable = false)
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentOrderStatus status = PaymentOrderStatus.EN_ATTENTE;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private LocalDateTime paymentDate;

    @Column(length = 100)
    private String paymentReference;

    @Column(length = 500)
    private String adminComment;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "paymentOrder", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<ReimbursedLine> reimbursedLines = new ArrayList<>();

    // Méthode utilitaire pour calculer le total
    public Double calculateTotalAmount() {
        return reimbursedLines.stream()
                .mapToDouble(ReimbursedLine::getReimbursedAmount)
                .sum();
    }
}