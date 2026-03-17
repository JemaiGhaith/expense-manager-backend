package com.coralio.chatbotmicroservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Iterator;

@Slf4j
@Service
public class RulesDataService {
    private JsonNode rulesData;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            InputStream inputStream = new ClassPathResource("static/rules-data.json").getInputStream();
            rulesData = objectMapper.readTree(inputStream).get("regles_statiques");
            log.info("✅ Règles statiques chargées");
        } catch (Exception e) {
            log.error("❌ Erreur chargement: {}", e.getMessage());
            rulesData = objectMapper.createObjectNode();
        }
    }

    /**
     * Retourne UNIQUEMENT les règles statiques (rien d'autre)
     */
    public String getStaticRulesOnly() {
        StringBuilder context = new StringBuilder();

        context.append("╔════════════════════════════════════════════════════════════╗\n");
        context.append("║     RÈGLES OFFICIELLES - SOURCE UNIQUE DE VÉRITÉ         ║\n");
        context.append("╚════════════════════════════════════════════════════════════╝\n\n");

        // ===== FORMULES MATHÉMATIQUES EXPLICITES =====
        context.append("📐 **FORMULES OFFICIELLES (à utiliser textuellement)**\n");
        context.append("Remboursement = MIN(montant_dépensé, plafond_de_la_catégorie)\n");
        context.append("Exemple 1: MIN(200, 150) = 150 TND remboursés\n");
        context.append("Exemple 2: MIN(100, 150) = 100 TND remboursés\n\n");

        // ===== PRINCIPES GÉNÉRAUX =====
        context.append("📋 **PRINCIPES GÉNÉRAUX:**\n");
        JsonNode principes = rulesData.get("principes_generaux");
        if (principes != null) {
            principes.forEach(p -> context.append("• ").append(p.asText()).append("\n"));
        }
        context.append("\n");

        // ===== RÈGLES JUSTIFICATIFS =====
        context.append("📎 **RÈGLES JUSTIFICATIFS:**\n");
        JsonNode justificatifs = rulesData.get("regles_justificatifs");
        if (justificatifs != null) {
            justificatifs.forEach(r -> context.append("• ").append(r.asText()).append("\n"));
        }
        context.append("\n");

        // ===== WORKFLOW =====
        context.append("🔄 **WORKFLOW DE VALIDATION:**\n");
        JsonNode workflow = rulesData.get("workflow_validation");
        if (workflow != null) {
            workflow.forEach(etape ->
                    context.append("• Étape ").append(etape.get("etape").asInt())
                            .append(": ").append(etape.get("nom").asText())
                            .append(" - ").append(etape.get("description").asText())
                            .append("\n"));
        }
        context.append("\n");

        // ===== FAQ - VERSION AMÉLIORÉE =====
        context.append("❓ **FOIRE AUX QUESTIONS (FAQ) - RÉPONSES OFFICIELLES**\n");
        context.append("╔════════════════════════════════════════════════════════════╗\n");
        context.append("║  Les questions et réponses suivantes sont OFFICIELLES    ║\n");
        context.append("║  et doivent être utilisées textuellement                 ║\n");
        context.append("╚════════════════════════════════════════════════════════════╝\n\n");

        JsonNode faqs = rulesData.get("faq");
        if (faqs != null) {
            int index = 1;
            for (JsonNode faq : faqs) {
                context.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                context.append("📌 **FAQ #").append(index++).append("**\n");
                context.append("❓ **QUESTION OFFICIELLE:** ").append(faq.get("question").asText()).append("\n");
                context.append("✅ **RÉPONSE OFFICIELLE:** ").append(faq.get("reponse").asText()).append("\n\n");
            }
        }

        // ===== ALERTES =====
        context.append("🚨 **SYSTÈME D'ALERTES:**\n");
        JsonNode alertes = rulesData.get("alertes");
        if (alertes != null) {
            alertes.forEach(alerte -> {
                context.append("• ").append(alerte.get("nom").asText()).append(": ");
                context.append(alerte.get("description").asText()).append("\n");
                context.append("  Action: ").append(alerte.get("action").asText()).append("\n\n");
            });
        }

        // ===== EXEMPLES =====
        context.append("📊 **EXEMPLES OFFICIELS:**\n");
        JsonNode exemples = rulesData.get("remboursement_partiel_exemples");
        if (exemples != null) {
            exemples.forEach(ex -> {
                context.append("• ").append(ex.get("scenario").asText()).append(":\n");
                context.append("  Montant: ").append(ex.get("montant_declare").asDouble())
                        .append(" TND, Plafond: ").append(ex.get("plafond").asDouble())
                        .append(" TND → ").append(ex.get("remboursement").asDouble())
                        .append(" TND remboursés (règle MIN(montant, plafond))\n");
            });
        }

        // ===== JOURS FÉRIÉS =====
        context.append("\n📅 **JOURS FÉRIÉS TUNISIENS:**\n");
        JsonNode jours = rulesData.get("jours_feries_tunisiens");
        if (jours != null) {
            jours.forEach(j -> context.append("• ").append(j.asText()).append("\n"));
        }

        // ===== RAPPEL FINAL =====
        context.append("\n🔴 **RAPPEL IMPORTANT**\n");
        context.append("TOUTE réponse DOIT être basée sur ces règles uniquement.\n");
        context.append("Si une question correspond à une FAQ, utilise la réponse officielle.\n");

        return context.toString();
    }
}