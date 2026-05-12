package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.client.NotificationClient;
import com.coralio.expense_management_microservice.client.UserServiceClient;
import com.coralio.expense_management_microservice.dto.LineReimbursementDTO;
import com.coralio.expense_management_microservice.dto.ReimbursementRequestDTO;
import com.coralio.expense_management_microservice.entities.*;
import com.coralio.expense_management_microservice.repos.ExpenseLineRepository;
import com.coralio.expense_management_microservice.repos.ExpenseNoteRepository;
import com.coralio.expense_management_microservice.repos.PaymentOrderRepository;
import com.coralio.expense_management_microservice.repos.ReimbursedLineRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReimbursementService {

    private static final Logger log = LoggerFactory.getLogger(ReimbursementService.class);

    private final ExpenseNoteRepository expenseNoteRepository;
    private final ExpenseLineRepository expenseLineRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final ReimbursedLineRepository reimbursedLineRepository;
    private final PdfGenerationService pdfGenerationService;
    private final CategoryService categoryService;
    private final NotificationClient notificationClient;
    private final UserServiceClient userServiceClient;

    // Updated constructor with notification dependencies
    public ReimbursementService(
            ExpenseNoteRepository expenseNoteRepository,
            ExpenseLineRepository expenseLineRepository,
            PaymentOrderRepository paymentOrderRepository,
            ReimbursedLineRepository reimbursedLineRepository,
            PdfGenerationService pdfGenerationService,
            CategoryService categoryService,
            NotificationClient notificationClient,
            UserServiceClient userServiceClient) {
        this.expenseNoteRepository = expenseNoteRepository;
        this.expenseLineRepository = expenseLineRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.reimbursedLineRepository = reimbursedLineRepository;
        this.pdfGenerationService = pdfGenerationService;
        this.categoryService = categoryService;
        this.notificationClient = notificationClient;
        this.userServiceClient = userServiceClient;
    }

    /**
     * Crée un ordre de paiement basé sur les lignes sélectionnées et leurs montants remboursés
     */
    @Transactional
    public PaymentOrder createPaymentOrder(ReimbursementRequestDTO request) {
        log.info("Création d'un ordre de paiement pour la note #{}", request.getExpenseNoteId());

        // 1. Vérifier que la note existe et est validée
        ExpenseNote note = expenseNoteRepository.findById(request.getExpenseNoteId())
                .orElseThrow(() -> new RuntimeException("Note de frais non trouvée avec l'ID: " + request.getExpenseNoteId()));

        if (note.getStatus() != ExpenseStatus.VALIDEE) {
            throw new IllegalStateException(
                    String.format("Seules les notes validées peuvent être remboursées. Statut actuel: %s",
                            note.getStatus())
            );
        }

        // 2. Vérifier qu'il n'y a pas déjà un ordre de paiement
        if (paymentOrderRepository.existsByExpenseNoteId(note.getId())) {
            throw new IllegalStateException("Un ordre de paiement existe déjà pour cette note");
        }

        // 3. Récupérer toutes les lignes de la note
        List<ExpenseLine> allLines = expenseLineRepository.findByExpenseNoteId(note.getId());
        Map<Long, ExpenseLine> linesMap = new HashMap<>();
        for (ExpenseLine line : allLines) {
            linesMap.put(line.getId(), line);
        }

        // 4. Créer l'ordre de paiement
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setExpenseNote(note);
        paymentOrder.setStatus(PaymentOrderStatus.EN_ATTENTE);
        paymentOrder.setPaymentMethod(request.getPaymentMethod());
        paymentOrder.setPaymentReference(request.getPaymentReference());
        paymentOrder.setAdminComment(request.getAdminComment());

        // AJOUTER LES INFOS DE DEVISE
        String displayCurrency = request.getDisplayCurrency() != null ? request.getDisplayCurrency() : "TND";
        Double exchangeRate = request.getExchangeRate() != null ? request.getExchangeRate() : 1.0;
        paymentOrder.setDisplayCurrency(displayCurrency);
        paymentOrder.setExchangeRate(exchangeRate);

        List<ReimbursedLine> reimbursedLines = new ArrayList<>();
        double totalReimbursed = 0.0;
        double totalReimbursedTND = 0.0;

        // 5. Traiter chaque ligne de la requête
        for (LineReimbursementDTO lineReimb : request.getLines()) {
            ExpenseLine line = linesMap.get(lineReimb.getLineId());

            if (line == null) {
                throw new IllegalArgumentException(
                        "La ligne avec l'ID " + lineReimb.getLineId() + " n'appartient pas à cette note"
                );
            }

            Double reimbursedAmount = lineReimb.getReimbursedAmount();

            if (reimbursedAmount == null || reimbursedAmount <= 0) {
                log.debug("Ligne #{} non remboursée", line.getId());
                continue;
            }

            Double categoryCeiling = categoryService.getPlafondByCategoryId(line.getCategoryId());
            String categoryName = categoryService.getCategoryName(line.getCategoryId());

            validateReimbursedAmount(line, reimbursedAmount, categoryCeiling, categoryName);

            boolean isFullyReimbursed = Math.abs(reimbursedAmount - line.getAmount()) < 0.01;

            ReimbursedLine reimbursedLine = new ReimbursedLine();
            reimbursedLine.setExpenseLine(line);
            reimbursedLine.setPaymentOrder(paymentOrder);
            reimbursedLine.setOriginalAmount(line.getAmount());
            reimbursedLine.setReimbursedAmount(reimbursedAmount);
            reimbursedLine.setIsFullyReimbursed(isFullyReimbursed);
            reimbursedLine.setAdminComment(lineReimb.getComment());

            // CALCULER LE MONTANT DANS LA DEVISE D'AFFICHAGE
            double amountInDisplayCurrency = reimbursedAmount * exchangeRate;
            reimbursedLine.setReimbursedAmountDisplay(amountInDisplayCurrency);

            reimbursedLines.add(reimbursedLine);
            totalReimbursed += reimbursedAmount;
            totalReimbursedTND += reimbursedAmount;

            String reimbursementType = isFullyReimbursed ? "TOTAL" : "PARTIEL";
            log.info("Ligne #{}: {} - {}{} (original: {}{}, plafond: {}{})",
                    line.getId(), reimbursementType,
                    String.format("%.2f", amountInDisplayCurrency), displayCurrency,
                    line.getAmount(), "TND", categoryCeiling, "TND");
        }

        if (reimbursedLines.isEmpty()) {
            throw new IllegalArgumentException("Aucune ligne sélectionnée pour le remboursement");
        }

        paymentOrder.setReimbursedLines(reimbursedLines);
        paymentOrder.setTotalAmount(totalReimbursed);
        paymentOrder.setTotalAmountOriginalTND(totalReimbursedTND);

        PaymentOrder savedOrder = paymentOrderRepository.save(paymentOrder);

        // ==================== SEND ADMIN VALIDATION NOTIFICATION ====================
        try {
            String employeeEmail = userServiceClient.getUserEmail(note.getEmployeeId());
            double convertedTotal = totalReimbursed * exchangeRate;  // already have exchangeRate and totalReimbursed
            notificationClient.notifyExpenseValidatedByAdmin(
                    UUID.fromString(note.getEmployeeId()),
                    employeeEmail,
                    "EXP-" + note.getId(),
                    note.getTotalAmount(),          // original TND amount
                    convertedTotal,                 // converted amount
                    displayCurrency,                // target currency
                    note.getId()
            );
            log.info("Admin validation notification sent for note {}", note.getId());
        } catch (Exception e) {
            log.error("Failed to send admin validation notification: {}", e.getMessage());
        }

        log.info("Ordre de paiement #{} créé avec succès. Montant total: {}{} (TND: {}{})",
                savedOrder.getId(),
                String.format("%.2f", totalReimbursed * exchangeRate), displayCurrency,
                totalReimbursed, "TND");

        return savedOrder;
    }

    /**
     * Valide le montant remboursé selon les règles métier
     */
    private void validateReimbursedAmount(ExpenseLine line, Double reimbursedAmount,
                                          Double categoryCeiling, String categoryName) {
        // Règle 1: Le montant remboursé ne peut pas être négatif
        if (reimbursedAmount < 0) {
            throw new IllegalArgumentException("Le montant remboursé ne peut pas être négatif");
        }

        // Règle 2: Le montant remboursé ne peut pas dépasser le montant saisi
        if (reimbursedAmount > line.getAmount() + 0.01) {
            throw new IllegalArgumentException(
                    String.format("Le montant remboursé (%.2f€) ne peut pas dépasser le montant saisi (%.2f€)",
                            reimbursedAmount, line.getAmount())
            );
        }

        // Règle 3: Si c'est un remboursement partiel (montant < montant saisi)
        if (reimbursedAmount < line.getAmount() - 0.01) {
            double maxPartialAmount = Math.min(line.getAmount(), categoryCeiling);

            if (reimbursedAmount > maxPartialAmount + 0.01) {
                throw new IllegalArgumentException(
                        String.format("Remboursement partiel limité à %.2f€ pour la catégorie '%s' (plafond: %.2f€, saisi: %.2f€)",
                                maxPartialAmount, categoryName, categoryCeiling, line.getAmount())
                );
            }
        }
    }

    /**
     * Confirme le paiement externe et met à jour les statuts
     */
    @Transactional
    public PaymentOrder confirmPayment(Long paymentOrderId, PaymentMethod method, String reference) {
        log.info("Confirmation du paiement pour l'ordre #{}", paymentOrderId);

        PaymentOrder order = paymentOrderRepository.findById(paymentOrderId)
                .orElseThrow(() -> new RuntimeException("Ordre de paiement non trouvé avec l'ID: " + paymentOrderId));

        if (order.getStatus() != PaymentOrderStatus.EN_ATTENTE) {
            throw new IllegalStateException(
                    String.format("Seuls les ordres en attente peuvent être confirmés. Statut actuel: %s",
                            order.getStatus())
            );
        }

        // Mettre à jour l'ordre de paiement
        order.setStatus(PaymentOrderStatus.PAYE);
        order.setPaymentMethod(method);
        order.setPaymentReference(reference);
        order.setPaymentDate(LocalDateTime.now());

        // Mettre à jour le statut de la note
        ExpenseNote note = order.getExpenseNote();
        note.setStatus(ExpenseStatus.REMBOURSEE);
        expenseNoteRepository.save(note);

        PaymentOrder savedOrder = paymentOrderRepository.save(order);

        // ==================== SEND REIMBURSEMENT NOTIFICATION ====================
        try {
            String employeeEmail = userServiceClient.getUserEmail(note.getEmployeeId());
            double convertedTotal = order.getTotalAmount() * order.getExchangeRate();  // order has exchangeRate and totalAmount in TND
            notificationClient.notifyExpenseReimbursed(
                    UUID.fromString(note.getEmployeeId()),
                    employeeEmail,
                    "EXP-" + note.getId(),
                    note.getTotalAmount(),          // original TND
                    convertedTotal,                 // converted amount
                    order.getDisplayCurrency(),     // stored currency
                    note.getId(),
                    "Admin"
            );
            log.info("Reimbursement notification sent for note {}", note.getId());
        } catch (Exception e) {
            log.error("Failed to send reimbursement notification: {}", e.getMessage());
        }

        // Générer le PDF pour archivage
        try {
            pdfGenerationService.generatePaymentOrderPdf(savedOrder);
            log.info("PDF généré pour l'ordre #{}", paymentOrderId);
        } catch (Exception e) {
            log.error("Erreur lors de la génération du PDF pour l'ordre #{}", paymentOrderId, e);
        }

        log.info("Paiement confirmé pour l'ordre #{}. Statut note: {}",
                paymentOrderId, ExpenseStatus.REMBOURSEE);

        return savedOrder;
    }

    /**
     * Récupère tous les ordres de paiement en attente
     */
    public List<PaymentOrder> getPendingPaymentOrders() {
        return paymentOrderRepository.findByStatusOrderByCreatedAtAsc(PaymentOrderStatus.EN_ATTENTE);
    }

    /**
     * Récupère un ordre de paiement par son ID
     */
    public PaymentOrder getPaymentOrder(Long id) {
        return paymentOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ordre de paiement non trouvé avec l'ID: " + id));
    }

    /**
     * Récupère l'ordre de paiement d'une note
     */
    public PaymentOrder getPaymentOrderByExpenseNoteId(Long expenseNoteId) {
        return paymentOrderRepository.findByExpenseNoteId(expenseNoteId)
                .orElseThrow(() -> new RuntimeException("Aucun ordre de paiement pour la note #" + expenseNoteId));
    }

    /**
     * Calcule les montants maximums remboursables pour chaque ligne d'une note
     */
    public Map<Long, ReimbursementLimit> calculateReimbursementLimits(Long expenseNoteId) {
        List<ExpenseLine> lines = expenseLineRepository.findByExpenseNoteId(expenseNoteId);
        Map<Long, ReimbursementLimit> limits = new HashMap<>();

        for (ExpenseLine line : lines) {
            Double ceiling = categoryService.getPlafondByCategoryId(line.getCategoryId());
            String categoryName = categoryService.getCategoryName(line.getCategoryId());

            double maxPartial = Math.min(line.getAmount(), ceiling);

            limits.put(line.getId(), new ReimbursementLimit(
                    line.getAmount(),
                    ceiling,
                    maxPartial,
                    categoryName,
                    line.getAmount() > ceiling
            ));
        }

        return limits;
    }

    /**
     * Classe interne pour les limites de remboursement
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ReimbursementLimit {
        private Double originalAmount;
        private Double categoryCeiling;
        private Double maxPartialAmount;
        private String categoryName;
        private Boolean exceedsCeiling;
    }

    /**
     * Récupère tous les ordres de paiement
     */
    public List<PaymentOrder> getAllPaymentOrders() {
        return paymentOrderRepository.findAll();
    }

    /**
     * Récupère les ordres par statut
     */
    public List<PaymentOrder> getPaymentOrdersByStatus(PaymentOrderStatus status) {
        return paymentOrderRepository.findByStatus(status);
    }
}