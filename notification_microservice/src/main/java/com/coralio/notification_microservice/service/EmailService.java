package com.coralio.notification_microservice.service;

import com.coralio.notification_microservice.entity.Notification;
import com.coralio.notification_microservice.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import jakarta.mail.internet.MimeMessage;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.company-name:Coral-io}")
    private String companyName;

    @Async
    public void sendNotificationEmail(Notification notification) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(notification.getUserEmail());
            helper.setSubject(generateSubject(notification));

            if (notification.getData() != null && notification.getData().containsKey("ccEmail")) {
                String ccEmail = (String) notification.getData().get("ccEmail");
                helper.setCc(ccEmail);
            }

            Context context = new Context();
            context.setLocale(Locale.FRENCH);
            context.setVariable("notification", notification);
            context.setVariable("frontendUrl", frontendUrl);
            context.setVariable("companyName", companyName);
            context.setVariable("currentYear", java.time.Year.now().getValue());

            if (notification.getCreatedAt() != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                context.setVariable("formattedDate", notification.getCreatedAt().format(formatter));
            }

            context.setVariable("buttonText", getButtonText(notification.getType()));
            context.setVariable("buttonColor", getButtonColor(notification.getType()));
            context.setVariable("icon", getIcon(notification.getType()));

            String templateName = getTemplateName(notification.getType());
            String htmlContent = templateEngine.process(templateName, context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Email sent successfully to: {} for notification: {} using template: {}",
                    notification.getUserEmail(), notification.getId(), templateName);

        } catch (Exception e) {
            log.error("❌ Failed to send email for notification {}: {}", notification.getId(), e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }

    @Async
    public void sendDigestEmail(String to, String userName, Map<String, Object> digestData) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("📊 Votre résumé Coral-io - " +
                    java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

            Context context = new Context();
            context.setLocale(Locale.FRENCH);
            context.setVariable("userName", userName);
            context.setVariable("frontendUrl", frontendUrl);
            context.setVariable("digestData", digestData);
            context.setVariable("currentYear", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("emails/daily-digest", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Digest email sent to: {}", to);

        } catch (Exception e) {
            log.error("❌ Failed to send digest email: {}", e.getMessage());
        }
    }

    @Async
    public void sendWelcomeEmail(String to, String name, String username, String temporaryPassword) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("🎉 Bienvenue sur Coral-io - Vos identifiants de connexion");

            Context context = new Context();
            context.setLocale(Locale.FRENCH);
            context.setVariable("name", name);
            context.setVariable("username", username);
            context.setVariable("temporaryPassword", temporaryPassword);
            context.setVariable("frontendUrl", frontendUrl);
            context.setVariable("currentYear", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("emails/welcome", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Welcome email sent to: {}", to);

        } catch (Exception e) {
            log.error("❌ Failed to send welcome email: {}", e.getMessage());
            throw new RuntimeException("Welcome email sending failed", e);
        }
    }

    @Async
    public void sendPasswordResetEmail(String to, String name, String resetToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("🔐 Réinitialisation de votre mot de passe - Coral-io");

            String resetUrl = frontendUrl + "/reset-password?token=" + resetToken;

            Context context = new Context();
            context.setLocale(Locale.FRENCH);
            context.setVariable("name", name);
            context.setVariable("resetUrl", resetUrl);
            context.setVariable("frontendUrl", frontendUrl);
            context.setVariable("currentYear", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("emails/password-reset", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Password reset email sent to: {}", to);

        } catch (Exception e) {
            log.error("❌ Failed to send password reset email: {}", e.getMessage());
            throw new RuntimeException("Password reset email sending failed", e);
        }
    }

    private String generateSubject(Notification notification) {
        return switch (notification.getType()) {
            case EXPENSE_APPROVED -> "✅ " + notification.getTitle();
            case EXPENSE_REJECTED -> "❌ " + notification.getTitle();
            case EXPENSE_REIMBURSED -> "💰 " + notification.getTitle();
            case EXPENSE_PENDING -> "⏳ " + notification.getTitle();
            case DOCUMENTS_MISSING -> "📄 " + notification.getTitle();
            case DOCUMENTS_UPLOADED -> "📎 " + notification.getTitle();
            case PAYMENT_PROCESSED -> "💳 " + notification.getTitle();
            case USER_WELCOME -> "🎉 " + notification.getTitle();
            case PASSWORD_CHANGED -> "🔐 " + notification.getTitle();
            case SYSTEM_ALERT -> "⚠️ " + notification.getTitle();
            default -> "📬 " + notification.getTitle();
        };
    }

    private String getTemplateName(NotificationType type) {
        return switch (type) {
            case EXPENSE_APPROVED -> "emails/expense-approved";
            case EXPENSE_REJECTED -> "emails/expense-rejected";
            case EXPENSE_REIMBURSED -> "emails/expense-reimbursed";
            case EXPENSE_PENDING -> "emails/expense-pending";
            case DOCUMENTS_MISSING -> "emails/missing-documents";
            case PAYMENT_PROCESSED -> "emails/payment-processed";
            case USER_WELCOME -> "emails/welcome";
            case PASSWORD_CHANGED -> "emails/password-changed";
            case SYSTEM_ALERT -> "emails/system-alert";
            default -> "emails/general-notification";
        };
    }

    private String getButtonText(NotificationType type) {
        return switch (type) {
            case EXPENSE_APPROVED, EXPENSE_PENDING -> "Voir la note de frais";
            case EXPENSE_REJECTED -> "Corriger et renvoyer";
            case EXPENSE_REIMBURSED -> "Voir le remboursement";
            case DOCUMENTS_MISSING -> "Télécharger les justificatifs";
            case PAYMENT_PROCESSED -> "Voir la transaction";
            case USER_WELCOME -> "Se connecter";
            case PASSWORD_CHANGED -> "Modifier le mot de passe";
            case SYSTEM_ALERT -> "En savoir plus";
            default -> "Voir les détails";
        };
    }

    private String getButtonColor(NotificationType type) {
        return switch (type) {
            case EXPENSE_APPROVED -> "#28a745";
            case EXPENSE_REJECTED -> "#dc3545";
            case EXPENSE_REIMBURSED -> "#17a2b8";
            case DOCUMENTS_MISSING -> "#ffc107";
            case SYSTEM_ALERT -> "#fd7e14";
            default -> "#667eea";
        };
    }

    private String getIcon(NotificationType type) {
        return switch (type) {
            case EXPENSE_APPROVED -> "✅";
            case EXPENSE_REJECTED -> "❌";
            case EXPENSE_REIMBURSED -> "💰";
            case DOCUMENTS_MISSING -> "📄";
            case SYSTEM_ALERT -> "⚠️";
            default -> "📬";
        };
    }
}