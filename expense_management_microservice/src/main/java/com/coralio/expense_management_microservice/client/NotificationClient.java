// expense-microservice/src/main/java/com/coralio/expense_management_microservice/client/NotificationClient.java
package com.coralio.expense_management_microservice.client;

import com.coralio.expense_management_microservice.dto.NotificationRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationClient {

    private final RestTemplate restTemplate;

    @Value("${notification.service.url:http://localhost:8888}")
    private String notificationServiceUrl;

    @Value("${notification.service.enabled:true}")
    private boolean notificationEnabled;

    /**
     * Send notification when expense is created
     */
    public void notifyExpenseCreated(UUID employeeId, String employeeEmail,
                                     String expenseReference, Double amount, Long expenseId) {
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("expenseId", expenseId);
        data.put("expenseReference", expenseReference);
        data.put("amount", amount);
        data.put("detailsUrl", "/expenses/" + expenseId);

        sendNotification(
                employeeId,
                employeeEmail,
                "EXPENSE_CREATED",
                "📝 Expense Created",
                "Your expense " + expenseReference + " has been created successfully",
                data,
                "NORMAL",
                expenseId,
                false  // Don't send email for creation
        );
    }

    /**
     * Send notification when expense is approved
     */
    public void notifyExpenseApproved(UUID employeeId, String employeeEmail,
                                      String expenseReference, Double amount,
                                      Long expenseId, String approverName, String comment) {
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("expenseId", expenseId);
        data.put("expenseReference", expenseReference);
        data.put("amount", amount);
        data.put("approverName", approverName);
        data.put("comment", comment);
        data.put("detailsUrl", "/expenses/" + expenseId);

        sendNotification(
                employeeId,
                employeeEmail,
                "EXPENSE_APPROVED",
                "✅ Expense Approved",
                "Your expense " + expenseReference + " has been approved by " + approverName + " for " + amount + " €",
                data,
                "HIGH",  // Send email for approvals
                expenseId,
                true
        );
    }

    /**
     * Send notification when expense is rejected
     */
    public void notifyExpenseRejected(UUID employeeId, String employeeEmail,
                                      String expenseReference, Double amount,
                                      Long expenseId, String approverName, String reason) {
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("expenseId", expenseId);
        data.put("expenseReference", expenseReference);
        data.put("amount", amount);
        data.put("rejectedBy", approverName);
        data.put("reason", reason);
        data.put("detailsUrl", "/expenses/" + expenseId);

        sendNotification(
                employeeId,
                employeeEmail,
                "EXPENSE_REJECTED",
                "❌ Expense Rejected",
                "Your expense " + expenseReference + " has been rejected" + (reason != null ? ": " + reason : ""),
                data,
                "HIGH",  // Send email for rejections
                expenseId,
                true
        );
    }

    /**
     * Send notification when expense is reimbursed
     */
    public void notifyExpenseReimbursed(UUID employeeId, String employeeEmail,
                                        String expenseReference, Double amount,
                                        Long expenseId, String reimbursedBy) {
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("expenseId", expenseId);
        data.put("expenseReference", expenseReference);
        data.put("amount", amount);
        data.put("reimbursedBy", reimbursedBy);
        data.put("detailsUrl", "/expenses/" + expenseId);

        sendNotification(
                employeeId,
                employeeEmail,
                "EXPENSE_REIMBURSED",
                "💰 Expense Reimbursed",
                "Your expense " + expenseReference + " has been reimbursed for " + amount + " €",
                data,
                "NORMAL",  // Normal priority for reimbursements
                expenseId,
                true
        );
    }

    /**
     * Send notification for missing documents
     */
    public void notifyMissingDocuments(UUID employeeId, String employeeEmail,
                                       String expenseReference, Long expenseId,
                                       int missingCount, int deadlineHours) {
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("expenseId", expenseId);
        data.put("expenseReference", expenseReference);
        data.put("missingCount", missingCount);
        data.put("deadlineHours", deadlineHours);
        data.put("detailsUrl", "/expenses/" + expenseId + "/documents");

        sendNotification(
                employeeId,
                employeeEmail,
                "DOCUMENTS_MISSING",
                "📄 Documents Missing",
                "Your expense " + expenseReference + " requires " + missingCount + " document(s). Please upload within " + deadlineHours + " hours.",
                data,
                "HIGH",
                expenseId,
                true
        );
    }

    /**
     * Send notification to manager about pending approvals
     */
    public void notifyManagerPendingApprovals(UUID managerId, String managerEmail,
                                              int pendingCount, String employeeName) {
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("pendingCount", pendingCount);
        data.put("employeeName", employeeName);
        data.put("detailsUrl", "/manager/pending-approvals");

        sendNotification(
                managerId,
                managerEmail,
                "EXPENSE_PENDING",
                "⏳ Pending Approval",
                employeeName + " has " + pendingCount + " expense(s) waiting for your approval",
                data,
                "NORMAL",
                null,
                true
        );
    }

    /**
     * Core method to send notification to the notification service
     */
    private void sendNotification(UUID userId, String userEmail, String type,
                                  String title, String message, Map<String, Object> data,
                                  String priority, Long sourceEntityId, boolean sendEmail) {
        try {
            NotificationRequestDTO request = NotificationRequestDTO.builder()
                    .userId(userId)
                    .userEmail(userEmail)
                    .type(type)
                    .title(title)
                    .message(message)
                    .data(data)
                    .priority(priority)
                    .sourceService("expense-service")
                    .sourceEntityId(sourceEntityId != null ? sourceEntityId.toString() : null)
                    .sourceEntityType("EXPENSE_NOTE")
                    .sendEmail(sendEmail)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Key", "notification-service-secret-key-2024");

            HttpEntity<NotificationRequestDTO> entity = new HttpEntity<>(request, headers);

            String url = notificationServiceUrl + "/api/notifications";
            log.info("📤 Sending notification: {} for user: {}", type, userId);

            restTemplate.postForEntity(url, entity, Void.class);
            log.info("✅ Notification sent successfully");

        } catch (Exception e) {
            log.error("❌ Failed to send notification: {}", e.getMessage());
            // Don't throw - notification failure shouldn't break expense operation
        }
    }
    // Dans NotificationClient.java - ajoutez ces méthodes:

    /**
     * Send notification to manager when a new expense is pending approval
     */
    // NotificationClient.java - CORRIGER LES TYPES
    public void notifyManagerPendingApproval(UUID managerId, String managerEmail,
                                             String employeeName, String expenseReference,
                                             Double amount, Long expenseId) {
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("expenseId", expenseId);
        data.put("expenseReference", expenseReference);
        data.put("amount", amount);
        data.put("employeeName", employeeName);
        data.put("detailsUrl", "/manager/expenses/" + expenseId);

        sendNotification(
                managerId,
                managerEmail,
                "EXPENSE_PENDING",  // ✅ Changé de "PENDING_APPROVAL" à "EXPENSE_PENDING"
                "⏳ Nouvelle note en attente",
                employeeName + " a soumis une nouvelle note de frais de " + amount + " € à approuver",
                data,
                "NORMAL",
                expenseId,
                true
        );
    }

    public void notifyCategoryLimitExceeded(UUID managerId, String managerEmail,
                                            String employeeName, String categoryName,
                                            Double amount, Double limit, Long expenseId) {
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("expenseId", expenseId);
        data.put("employeeName", employeeName);
        data.put("categoryName", categoryName);
        data.put("amount", amount);
        data.put("limit", limit);
        data.put("excessAmount", amount - limit);
        data.put("detailsUrl", "/manager/expenses/" + expenseId);

        sendNotification(
                managerId,
                managerEmail,
                "SYSTEM_ALERT",  // ✅ Changé de "CATEGORY_LIMIT_EXCEEDED" à "SYSTEM_ALERT"
                "⚠️ Dépassement de plafond",
                employeeName + " a dépassé le plafond de la catégorie '" + categoryName +
                        "' : " + amount + " € (plafond: " + limit + " €)",
                data,
                "HIGH",
                expenseId,
                false
        );
    }

    public void notifyBudgetLimitExceeded(UUID managerId, String managerEmail,
                                          String projectName, String employeeName,
                                          Double amount, Double remainingBudget, Long expenseId,
                                          String alertType) {  // "NOTE_EXCESSIVE" ou "BUDGET_OVERUN"
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("expenseId", expenseId);
        data.put("projectName", projectName);
        data.put("employeeName", employeeName);
        data.put("amount", amount);
        data.put("remainingBudget", remainingBudget);
        data.put("alertType", alertType);
        data.put("detailsUrl", "/manager/expenses/" + expenseId);

        String title, message;
        if ("NOTE_EXCESSIVE".equals(alertType)) {
            title = "⚠️ Note excessive";
            message = "La dépense de " + amount + " € de " + employeeName +
                    " dépasse à elle seule le budget total du projet '" + projectName + "'";
        } else {
            title = "⚠️ Dépassement de budget projet";
            message = "La dépense de " + amount + " € de " + employeeName +
                    " dépasse le budget restant du projet '" + projectName +
                    "' (" + remainingBudget + " € restants)";
        }

        sendNotification(
                managerId,
                managerEmail,
                "BUDGET_LIMIT_EXCEEDED",
                title,
                message,
                data,
                "CRITICAL",
                expenseId,
                false
        );
    }
    /**
     * Send notification to admin when notes are ready for reimbursement
     */
    public void notifyAdminNotesReadyForReimbursement(UUID adminId, String adminEmail,
                                                      int pendingCount, Double totalAmount,
                                                      List<Map<String, Object>> notesList) {
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("pendingCount", pendingCount);
        data.put("totalAmount", totalAmount);
        data.put("notesList", notesList);
        data.put("detailsUrl", "/admin/pending-reimbursements");

        sendNotification(
                adminId,
                adminEmail,
                "NOTES_READY_FOR_REIMBURSEMENT",
                "💰 " + pendingCount + " note(s) prête(s) au remboursement",
                pendingCount + " note(s) de frais validées attendent d'être remboursées. Montant total: " + totalAmount + " €",
                data,
                "HIGH",
                null,
                true
        );
    }
    // Ajoutez ces méthodes dans NotificationClient.java

    /**
     * Send notification to admin when expense is validated by manager
     */
    // Dans NotificationClient.java

    /**
     * Send notification to admin when expense is validated by manager
     */
    public void notifyAdminExpenseValidated(UUID adminId, String adminEmail,
                                            String employeeName, String expenseReference,
                                            Double amount, Long expenseId, String managerName) {
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("expenseId", expenseId);
        data.put("expenseReference", expenseReference);
        data.put("amount", amount);
        data.put("employeeName", employeeName);
        data.put("managerName", managerName);
        data.put("detailsUrl", "/admin/expenses/" + expenseId);

        sendNotification(
                adminId,
                adminEmail,
                "EXPENSE_VALIDATED_BY_MANAGER",  // ✅ Type spécifique
                "✅ Note validée par manager",
                employeeName + " - Note " + expenseReference + " a été validée par " + managerName + " (" + amount + " €)",
                data,
                "NORMAL",
                expenseId,
                true
        );
    }

    /**
     * Send notification to admin when expense has budget overrun
     * Utilise BUDGET_LIMIT_EXCEEDED existant
     */
    public void notifyAdminBudgetOverrun(UUID adminId, String adminEmail,
                                         String projectName, String employeeName,
                                         Double amount, Double budget, Long expenseId) {
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("expenseId", expenseId);
        data.put("projectName", projectName);
        data.put("employeeName", employeeName);
        data.put("amount", amount);
        data.put("budget", budget);
        data.put("excessAmount", amount - budget);
        data.put("detailsUrl", "/admin/expenses/" + expenseId);

        sendNotification(
                adminId,
                adminEmail,
                "BUDGET_LIMIT_EXCEEDED",  // ✅ Type existant
                "⚠️ Dépassement de budget projet",
                "Budget dépassé sur projet '" + projectName + "' : " + employeeName + " - " + amount + " € (budget: " + budget + " €)",
                data,
                "CRITICAL",
                expenseId,
                false
        );
    }

    /**
     * Send notification to admin when expense has category limit overrun
     * Utilise CATEGORY_LIMIT_EXCEEDED existant
     */
    public void notifyAdminCategoryLimitOverrun(UUID adminId, String adminEmail,
                                                String categoryName, String employeeName,
                                                Double amount, Double limit, Long expenseId) {
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("expenseId", expenseId);
        data.put("categoryName", categoryName);
        data.put("employeeName", employeeName);
        data.put("amount", amount);
        data.put("limit", limit);
        data.put("excessAmount", amount - limit);
        data.put("detailsUrl", "/admin/expenses/" + expenseId);

        sendNotification(
                adminId,
                adminEmail,
                "CATEGORY_LIMIT_EXCEEDED",  // ✅ Type existant
                "⚠️ Dépassement de plafond catégorie",
                "Plafond dépassé pour la catégorie '" + categoryName + "' : " + employeeName + " - " + amount + " € (plafond: " + limit + " €)",
                data,
                "HIGH",
                expenseId,
                false
        );
    }
    /**
     * Send notification to admin when expense is rejected by manager
     */
    public void notifyAdminExpenseRejected(UUID adminId, String adminEmail,
                                           String employeeName, String expenseReference,
                                           Double amount, Long expenseId,
                                           String managerName, String reason) {
        if (!notificationEnabled) return;

        Map<String, Object> data = new HashMap<>();
        data.put("expenseId", expenseId);
        data.put("expenseReference", expenseReference);
        data.put("amount", amount);
        data.put("employeeName", employeeName);
        data.put("managerName", managerName);
        data.put("reason", reason);
        data.put("detailsUrl", "/admin/expenses/" + expenseId);

        sendNotification(
                adminId,
                adminEmail,
                "EXPENSE_REJECTED",  // Utilise le type existant
                "❌ Note refusée par manager",
                employeeName + " - Note " + expenseReference + " a été refusée par " + managerName +
                        (reason != null && !reason.isEmpty() ? " : " + reason : ""),
                data,
                "HIGH",
                expenseId,
                true
        );
    }
}