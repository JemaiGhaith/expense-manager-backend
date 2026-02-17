package com.coralio.user_microservice.services;

import com.coralio.user_microservice.dto.EmailCredentialsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendWelcomeEmail(EmailCredentialsDTO dto) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(dto.getTo());
            helper.setSubject("🎉 Bienvenue sur Coral-io - Vos identifiants de connexion");

            String htmlContent = buildHtmlEmailContent(dto);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Email envoyé à {}", dto.getTo());

        } catch (Exception e) {
            log.error("❌ Erreur envoi email: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de l'envoi de l'email", e);
        }
    }

    private String buildHtmlEmailContent(EmailCredentialsDTO dto) {
        String fullName = dto.getFirstName() + " " + dto.getLastName();

        return String.format("""
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Bienvenue sur Coral-io</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        margin: 0;
                        padding: 0;
                        background-color: #f4f4f4;
                    }
                    .container {
                        max-width: 600px;
                        margin: 20px auto;
                        background: white;
                        border-radius: 12px;
                        overflow: hidden;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.1);
                    }
                    .header {
                        background: linear-gradient(135deg, #2c3e50, #34495e);
                        color: white;
                        padding: 30px;
                        text-align: center;
                    }
                    .header h1 {
                        margin: 0;
                        font-size: 28px;
                        font-weight: 600;
                    }
                    .header p {
                        margin: 10px 0 0;
                        opacity: 0.9;
                        font-size: 16px;
                    }
                    .content {
                        padding: 30px;
                        background: white;
                    }
                    .greeting {
                        font-size: 18px;
                        color: #2c3e50;
                        margin-bottom: 20px;
                    }
                    .credentials-card {
                        background: #f8f9fa;
                        border-radius: 10px;
                        padding: 20px;
                        margin: 20px 0;
                        border-left: 4px solid #2c3e50;
                    }
                    .credential-item {
                        display: flex;
                        align-items: center;
                        margin: 12px 0;
                        padding: 8px;
                        background: white;
                        border-radius: 6px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.02);
                    }
                    .credential-icon {
                        width: 40px;
                        height: 40px;
                        background: linear-gradient(135deg, #2c3e50, #34495e);
                        border-radius: 8px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        margin-right: 15px;
                    }
                    .credential-icon span {
                        color: white;
                        font-size: 20px;
                    }
                    .credential-details {
                        flex: 1;
                    }
                    .credential-label {
                        font-size: 12px;
                        color: #7f8c8d;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                    }
                    .credential-value {
                        font-size: 16px;
                        font-weight: 600;
                        color: #2c3e50;
                        font-family: 'Courier New', monospace;
                    }
                    .info-box {
                        background: #e8f4fd;
                        border-radius: 8px;
                        padding: 15px;
                        margin: 20px 0;
                        border: 1px solid #b8daff;
                    }
                    .info-box p {
                        margin: 5px 0;
                        color: #004085;
                    }
                    .button {
                        display: inline-block;
                        padding: 14px 30px;
                        background: linear-gradient(135deg, #2c3e50, #34495e);
                        color: white;
                        text-decoration: none;
                        border-radius: 8px;
                        font-weight: 600;
                        margin: 20px 0;
                        transition: transform 0.2s;
                    }
                    .button:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 4px 12px rgba(44,62,80,0.2);
                    }
                    .footer {
                        background: #f8f9fa;
                        padding: 20px;
                        text-align: center;
                        border-top: 1px solid #dee2e6;
                    }
                    .footer p {
                        margin: 5px 0;
                        color: #6c757d;
                        font-size: 12px;
                    }
                    .warning {
                        color: #856404;
                        background: #fff3cd;
                        border: 1px solid #ffeeba;
                        border-radius: 8px;
                        padding: 12px;
                        margin: 20px 0;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🎉 Bienvenue sur Coral-io !</h1>
                        <p>Votre compte a été créé avec succès</p>
                    </div>
                    
                    <div class="content">
                        <div class="greeting">
                            Bonjour <strong>%s</strong>,
                        </div>
                        
                        <p>Nous avons le plaisir de vous accueillir sur la plateforme Coral-io. 
                        Votre compte a été créé par l'administrateur.</p>
                        
                        <div class="credentials-card">
                            <h3 style="margin-top: 0; color: #2c3e50;">🔐 Vos identifiants de connexion</h3>
                            
                            <div class="credential-item">
                                <div class="credential-icon">
                                    <span>👤</span>
                                </div>
                                <div class="credential-details">
                                    <div class="credential-label">Nom d'utilisateur</div>
                                    <div class="credential-value">%s</div>
                                </div>
                            </div>
                            
                            <div class="credential-item">
                                <div class="credential-icon">
                                    <span>🔑</span>
                                </div>
                                <div class="credential-details">
                                    <div class="credential-label">Mot de passe temporaire</div>
                                    <div class="credential-value">%s</div>
                                </div>
                            </div>
                            
                            <div class="credential-item">
                                <div class="credential-icon">
                                    <span>📧</span>
                                </div>
                                <div class="credential-details">
                                    <div class="credential-label">Email</div>
                                    <div class="credential-value">%s</div>
                                </div>
                            </div>
                        </div>
                        
                        <div class="info-box">
                            <p><strong>📋 Informations complémentaires :</strong></p>
                            <p>• Rôle : <strong>%s</strong></p>
                            <p>• Département : <strong>%s</strong></p>
                        </div>
                        
                        <div style="text-align: center;">
                            <a href="%s" class="button" target="_blank">
                                🌐 Se connecter à Coral-io
                            </a>
                        </div>
                        
                        <div class="warning">
                            <p><strong>⚠️ Important :</strong></p>
                            <p>• Pour des raisons de sécurité, changez votre mot de passe dès votre première connexion.</p>
                            <p>• Ne partagez jamais vos identifiants avec personne.</p>
                            <p>• En cas de problème, contactez l'administrateur.</p>
                        </div>
                    </div>
                    
                    <div class="footer">
                        <p>© 2024 Coral-io. Tous droits réservés.</p>
                        <p>Cet email a été envoyé automatiquement, merci de ne pas y répondre.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                fullName,
                dto.getUsername(),
                dto.getPassword(),
                dto.getEmail(),
                dto.getRole() != null ? dto.getRole() : "Employé",
                dto.getDepartmentName() != null ? dto.getDepartmentName() : "Non assigné",
                dto.getLoginUrl()
        );
    }
}