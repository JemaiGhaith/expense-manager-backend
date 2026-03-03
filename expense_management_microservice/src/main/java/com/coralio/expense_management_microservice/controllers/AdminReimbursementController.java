package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.LineReimbursementDTO;
import com.coralio.expense_management_microservice.dto.PaymentOrderDTO;
import com.coralio.expense_management_microservice.dto.ReimbursementRequestDTO;
import com.coralio.expense_management_microservice.entities.PaymentMethod;
import com.coralio.expense_management_microservice.entities.PaymentOrder;
import com.coralio.expense_management_microservice.entities.PaymentOrderStatus;
import com.coralio.expense_management_microservice.services.PaymentOrderMapperService;
import com.coralio.expense_management_microservice.services.PdfGenerationService;
import com.coralio.expense_management_microservice.services.ReimbursementService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/reimbursements")
public class AdminReimbursementController {

    private static final Logger log = LoggerFactory.getLogger(AdminReimbursementController.class);

    private final ReimbursementService reimbursementService;
    private final PdfGenerationService pdfGenerationService;
    private final PaymentOrderMapperService paymentOrderMapper;

    public AdminReimbursementController(
            ReimbursementService reimbursementService,
            PdfGenerationService pdfGenerationService,
            PaymentOrderMapperService paymentOrderMapper) {
        this.reimbursementService = reimbursementService;
        this.pdfGenerationService = pdfGenerationService;
        this.paymentOrderMapper = paymentOrderMapper;
    }

    /**
     * 1. Calculer les limites de remboursement pour une note
     * (aide pour le frontend)
     */
    @GetMapping("/limits/{expenseNoteId}")
    public ResponseEntity<Map<Long, Object>> getReimbursementLimits(@PathVariable Long expenseNoteId) {
        log.info("Calcul des limites pour la note #{}", expenseNoteId);

        var limits = reimbursementService.calculateReimbursementLimits(expenseNoteId);

        Map<Long, Object> response = new HashMap<>();
        limits.forEach((lineId, limit) -> {
            Map<String, Object> limitInfo = new HashMap<>();
            limitInfo.put("originalAmount", limit.getOriginalAmount());
            limitInfo.put("categoryCeiling", limit.getCategoryCeiling());
            limitInfo.put("maxPartialAmount", limit.getMaxPartialAmount());
            limitInfo.put("categoryName", limit.getCategoryName());
            limitInfo.put("exceedsCeiling", limit.getExceedsCeiling());
            limitInfo.put("warning", limit.getExceedsCeiling() ?
                    "Ce montant dépasse le plafond de " + limit.getCategoryCeiling() + "€" : null);

            response.put(lineId, limitInfo);
        });

        return ResponseEntity.ok(response);
    }

    /**
     * 2. Créer un ordre de paiement
     */
    @PostMapping("/create-order")
    public ResponseEntity<?> createPaymentOrder(@Valid @RequestBody ReimbursementRequestDTO request) {
        try {
            log.info("Création d'ordre de paiement pour la note #{}", request.getExpenseNoteId());

            // Log des lignes traitées
            for (LineReimbursementDTO line : request.getLines()) {
                if (line.getReimbursedAmount() != null && line.getReimbursedAmount() > 0) {
                    log.info("  Ligne #{}: {}€", line.getLineId(), line.getReimbursedAmount());
                }
            }

            PaymentOrder order = reimbursementService.createPaymentOrder(request);
            PaymentOrderDTO dto = paymentOrderMapper.toDto(order);

            return ResponseEntity.ok(dto);

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Erreur lors de la création de l'ordre: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur inattendue", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Erreur interne du serveur"));
        }
    }

    /**
     * 3. Lister les ordres en attente
     */
    @GetMapping("/pending-orders")
    public ResponseEntity<List<PaymentOrderDTO>> getPendingOrders() {
        log.info("Récupération des ordres en attente");

        List<PaymentOrder> orders = reimbursementService.getPendingPaymentOrders();
        List<PaymentOrderDTO> dtos = orders.stream()
                .map(paymentOrderMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * 4. Obtenir les détails d'un ordre
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<?> getOrderDetails(@PathVariable Long orderId) {
        try {
            log.info("Détails de l'ordre #{}", orderId);

            PaymentOrder order = reimbursementService.getPaymentOrder(orderId);
            PaymentOrderDTO dto = paymentOrderMapper.toDto(order);

            return ResponseEntity.ok(dto);

        } catch (RuntimeException e) {
            log.error("Ordre non trouvé: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 5. Confirmer le paiement externe
     */
    @PostMapping("/confirm-payment/{orderId}")
    public ResponseEntity<?> confirmPayment(
            @PathVariable Long orderId,
            @RequestParam PaymentMethod method,
            @RequestParam(required = false) String reference) {

        try {
            log.info("Confirmation paiement ordre #{} - Méthode: {}", orderId, method);

            PaymentOrder order = reimbursementService.confirmPayment(orderId, method, reference);
            PaymentOrderDTO dto = paymentOrderMapper.toDto(order);

            return ResponseEntity.ok(dto);

        } catch (IllegalStateException e) {
            log.error("Erreur lors de la confirmation: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Ordre non trouvé: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 6. Générer/Télécharger le PDF
     */
    @GetMapping("/pdf/{orderId}")
    public ResponseEntity<?> downloadPaymentOrderPdf(@PathVariable Long orderId) {
        try {
            log.info("Téléchargement PDF pour l'ordre #{}", orderId);

            PaymentOrder order = reimbursementService.getPaymentOrder(orderId);
            byte[] pdfContent = pdfGenerationService.generatePaymentOrderPdf(order);

            ByteArrayResource resource = new ByteArrayResource(pdfContent);

            String filename = String.format("ordre_paiement_%d_%s.pdf",
                    orderId,
                    java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdfContent.length)
                    .body(resource);

        } catch (RuntimeException e) {
            log.error("Ordre non trouvé: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erreur lors de la génération du PDF", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Erreur lors de la génération du PDF"));
        }
    }

    /**
     * 7. Vérifier si une note a déjà un ordre de paiement
     */
    @GetMapping("/check/{expenseNoteId}")
    public ResponseEntity<Map<String, Object>> checkPaymentOrderExists(@PathVariable Long expenseNoteId) {
        try {
            PaymentOrder order = reimbursementService.getPaymentOrderByExpenseNoteId(expenseNoteId);

            Map<String, Object> response = new HashMap<>();
            response.put("exists", true);
            response.put("orderId", order.getId());
            response.put("status", order.getStatus());
            response.put("statusDisplay", order.getStatus().getDisplayName());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("exists", false);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 8. Récupérer tous les ordres (filtrés par statut optionnel)
     */
    @GetMapping("/orders")
    public ResponseEntity<List<PaymentOrderDTO>> getAllOrders(
            @RequestParam(required = false) String status) {

        log.info("Récupération des ordres avec filtre status: {}", status);

        List<PaymentOrder> orders;
        if (status != null && !status.isEmpty()) {
            try {
                PaymentOrderStatus orderStatus = PaymentOrderStatus.valueOf(status.toUpperCase());
                orders = reimbursementService.getPaymentOrdersByStatus(orderStatus);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        } else {
            orders = reimbursementService.getAllPaymentOrders();
        }

        List<PaymentOrderDTO> dtos = orders.stream()
                .map(paymentOrderMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
}