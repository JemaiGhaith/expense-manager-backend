package com.coralio.chatbotmicroservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
public class RulesDataService {
    private JsonNode rulesData;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> answerCache = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            InputStream inputStream = new ClassPathResource("static/rules-data.json").getInputStream();
            rulesData = objectMapper.readTree(inputStream).get("regles_statiques");
            log.info("✅ Règles statiques chargées");
            buildAnswerCache();
        } catch (Exception e) {
            log.error("❌ Erreur chargement: {}", e.getMessage());
            rulesData = objectMapper.createObjectNode();
        }
    }

    private void buildAnswerCache() {
        // Règles de remboursement (priorité)
        answerCache.put("règles de remboursement", getReglesRemboursement());
        answerCache.put("regles de remboursement", getReglesRemboursement());
        answerCache.put("donne moi les règles de remboursement", getReglesRemboursement());
        answerCache.put("donne-moi les règles de remboursement", getReglesRemboursement());

        // Remboursements partiels
        answerCache.put("remboursement partiel", getRemboursementPartielDetaille());
        answerCache.put("remboursements partiels", getRemboursementPartielDetaille());
        answerCache.put("calcul remboursement", getRemboursementPartielDetaille());

        // Délai de remboursement
        answerCache.put("délai remboursement", getDelaiRemboursementDetaille());
        answerCache.put("temps remboursement", getDelaiRemboursementDetaille());

        // Justificatifs
        answerCache.put("formats justificatif", getFormatsJustificatifs());
        answerCache.put("taille justificatif", getTailleJustificatifs());

        // Workflow
        answerCache.put("workflow", getWorkflowDetaille());
        answerCache.put("validation", getWorkflowDetaille());

        // Alertes
        answerCache.put("facture en double", getAlerteFactureDoubleDetaille());
        answerCache.put("dépassement plafond", getAlerteDepassementPlafondDetaille());
        answerCache.put("justificatif illisible", getAlerteJustificatifIllisibleDetaille());
        answerCache.put("date incohérente", getAlerteDateIncoherenteDetaille());

        // Jours fériés
        answerCache.put("jours fériés", getJoursFeriesDetaille());

        log.info("✅ Cache construit avec {} entrées", answerCache.size());
    }

    public String answerQuestion(String question) {
        String lowerQuestion = question.toLowerCase();
        log.info("🔍 Recherche réponse pour: {}", lowerQuestion);

        // 1. ✅ PRIORITÉ 1: Règles de remboursement (détection spécifique)
        if (lowerQuestion.contains("règles de remboursement") ||
                lowerQuestion.contains("regles de remboursement") ||
                (lowerQuestion.contains("règle") && lowerQuestion.contains("remboursement")) ||
                (lowerQuestion.contains("regle") && lowerQuestion.contains("remboursement"))) {
            log.info("✅ Réponse: règles de remboursement");
            return getReglesRemboursement();
        }

        // 2. Recherche exacte dans le cache
        for (Map.Entry<String, String> entry : answerCache.entrySet()) {
            if (lowerQuestion.contains(entry.getKey())) {
                log.info("✅ Réponse trouvée dans le cache pour: {}", entry.getKey());
                return entry.getValue();
            }
        }

        // 3. Remboursements partiels
        if ((lowerQuestion.contains("remboursement") && lowerQuestion.contains("partiel")) ||
                (lowerQuestion.contains("calcul") && lowerQuestion.contains("remboursement"))) {
            return getRemboursementPartielDetaille();
        }

        // 4. Délai de remboursement
        if (lowerQuestion.contains("délai") || lowerQuestion.contains("temps")) {
            return getDelaiRemboursementDetaille();
        }

        // 5. Justificatifs
        if (lowerQuestion.contains("justificatif")) {
            if (lowerQuestion.contains("format")) {
                return getFormatsJustificatifs();
            }
            if (lowerQuestion.contains("taille")) {
                return getTailleJustificatifs();
            }
            return getReglesJustificatifs();
        }

        // 6. Workflow
        if (lowerQuestion.contains("workflow") || lowerQuestion.contains("validation")) {
            return getWorkflowDetaille();
        }

        // 7. Alertes
        if (lowerQuestion.contains("alerte")) {
            if (lowerQuestion.contains("facture") && lowerQuestion.contains("double")) {
                return getAlerteFactureDoubleDetaille();
            }
            if (lowerQuestion.contains("dépassement") || lowerQuestion.contains("plafond")) {
                return getAlerteDepassementPlafondDetaille();
            }
            if (lowerQuestion.contains("illisible")) {
                return getAlerteJustificatifIllisibleDetaille();
            }
            if (lowerQuestion.contains("date")) {
                return getAlerteDateIncoherenteDetaille();
            }
            return getAlertesDetaillees();
        }

        // 8. Jours fériés
        if (lowerQuestion.contains("férié") || lowerQuestion.contains("ferie")) {
            return getJoursFeriesDetaille();
        }

        // 9. Principes généraux (en dernier, après les règles de remboursement)
        if (lowerQuestion.contains("principe") || lowerQuestion.contains("général")) {
            return getPrincipesGenerauxDetaille();
        }

        // 10. Retourner toutes les règles
        return getStaticRulesOnly();
    }

    // ==================== RÉPONSES DÉTAILLÉES ====================

    private String getReglesRemboursement() {
        return """
            Voici les règles de remboursement :
            
            **1. Remboursement sur justificatifs ORIGINAUX**
            • Le remboursement est effectué uniquement sur des justificatifs originaux.
            
            **2. Délai de traitement : 5 jours ouvrés**
            • Le délai de traitement pour les notes de frais est de 5 jours ouvrés.
            
            **3. Conservation des justificatifs : 6 mois**
            • Les justificatifs doivent être conservés pendant au moins 6 mois.
            
            **4. Devise : TND (conversion possible en EUR, USD)**
            • La devise de base pour les remboursements est le Tunisian Dinar (TND),
              mais des conversions sont possibles vers l'Euro (EUR) et le Dollar américain (USD).
            
            Ces règles s'appliquent à toutes les notes de frais.
            """;
    }

    private String getRemboursementPartielDetaille() {
        return """
            Le remboursement partiel est calculé en fonction du montant dépensé et du plafond autorisé.
            
            La formule utilisée est :
            Remboursement = MIN(montant_dépensé, plafond)
            
            Cela signifie que le montant maximum qui sera remboursé est soit le montant dépensé, soit le plafond (ce qui est le plus bas).
            
            Exemple concret :
            • Vous avez dépensé 250 TND pour une restauration
            • Le plafond autorisé pour la restauration est de 200 TND
            • Votre remboursement sera de 200 TND
            • La différence de 50 TND restera à votre charge
            
            Cette formule permet d'assurer que les dépenses ne dépassent pas les plafonds autorisés et que les employés ne reçoivent pas plus qu'ils n'en ont droit.
            """;
    }

    private String getDelaiRemboursementDetaille() {
        return """
            Le délai de remboursement est de 5 jours ouvrables après validation complète.
            
            Voici le détail du processus :
            1. 📝 Validation manager : jusqu'à 48h
            2. 🔍 Contrôle admin : jusqu'à 48h
            3. 💰 Traitement paiement : jusqu'à 24h
            
            Total : 5 jours ouvrables
            
            Exemple : Si votre note est validée lundi, vous serez remboursé le lundi suivant.
            """;
    }

    private String getFormatsJustificatifs() {
        return """
            Les formats de justificatifs acceptés sont :
            • PDF (Portable Document Format) - recommandé
            • JPG / JPEG (Image)
            • PNG (Image)
            • DOC / DOCX (Microsoft Word)
            
            Taille maximale : 10 Mo par fichier
            """;
    }

    private String getTailleJustificatifs() {
        return """
            La taille maximale autorisée pour un justificatif est de 10 Mo par fichier.
            
            Si votre fichier dépasse cette limite, vous pouvez :
            • Compresser l'image (réduire la résolution)
            • Convertir en PDF (souvent plus petit)
            • Scanner en noir et blanc si possible
            
            Un scan en 300 DPI en PDF fait généralement entre 1 et 3 Mo.
            """;
    }

    private String getReglesJustificatifs() {
        return """
            Règles pour les justificatifs :
            
            • Formats acceptés : PDF, JPG, PNG, DOC, DOCX
            • Taille maximale : 10 Mo par fichier
            • Un justificatif par ligne de frais
            • Conservation obligatoire : 6 mois
            • Justificatifs illisibles → ALERTE
            
            Bonnes pratiques :
            • Scannez en haute résolution (min 300 DPI)
            • Nommez vos fichiers de façon descriptive
            • Évitez les caractères spéciaux
            """;
    }

    private String getWorkflowDetaille() {
        return """
            Le workflow de validation se déroule en 5 étapes :
            
            1. 📝 **Brouillon** - Saisie par l'employé
               Création de la note et ajout des justificatifs
            
            2. ⏳ **Soumise** - En attente validation manager
               Le manager vérifie les règles générales (max 48h)
            
            3. ✅/❌ **Validée / Refusée** - Décision du manager
               • Validée → Passe au contrôle admin
               • Refusée → Retour à l'employé avec commentaire
            
            4. 🔍 **Contrôlée** - Vérification admin
               Vérification des justificatifs et des plafonds
            
            5. 💰 **Remboursée** - Paiement effectué
               Virement bancaire et clôture de la note
            
            Délai total : 5 jours ouvrables après validation complète
            """;
    }

    private String getAlerteFactureDoubleDetaille() {
        return """
            🔴 **Alerte : Facture en double**
            
            Cette alerte se déclenche lorsqu'une facture similaire a déjà été soumise récemment.
            
            Pourquoi cette alerte ?
            • Prévention des doublons
            • Éviter les erreurs de saisie
            • Détection de fraudes potentielles
            
            Que faire ?
            • Si c'est une erreur : annulez la note en double
            • Si c'est intentionnel : ajoutez un commentaire explicatif
            • La note sera examinée manuellement par l'administrateur
            
            Vous pouvez quand même soumettre votre note, elle sera vérifiée manuellement.
            """;
    }

    private String getAlerteDepassementPlafondDetaille() {
        return """
            🔴 **Alerte : Dépassement plafond**
            
            Cette alerte se déclenche lorsque le montant saisi dépasse le plafond autorisé pour la catégorie.
            
            Exemple :
            • Catégorie : Restauration
            • Plafond : 150 TND
            • Votre dépense : 200 TND
            • Résultat : Alerte déclenchée
            
            Action automatique :
            • Remboursement limité au plafond (150 TND)
            • La différence (50 TND) reste à votre charge
            
            Conseil : Avant de faire une dépense, vérifiez les plafonds dans la catégorie concernée.
            """;
    }

    private String getAlerteJustificatifIllisibleDetaille() {
        return """
            🟡 **Alerte : Justificatif illisible**
            
            Cette alerte se déclenche lorsque la qualité du justificatif est insuffisante (trop sombre, flou, mal cadré).
            
            Que faire ?
            • Re-scanner le document en meilleure qualité (min 300 DPI)
            • Utiliser un fond clair et bien cadrer le document
            • Fournir l'original à l'administrateur si nécessaire
            
            Conséquence :
            • La note sera examinée manuellement par l'administrateur
            • Vous pouvez quand même soumettre la note
            
            Bonnes pratiques :
            • Scanner en PDF en 300 DPI
            • Vérifier la lisibilité avant envoi
            • Éviter les ombres et reflets
            """;
    }

    private String getAlerteDateIncoherenteDetaille() {
        return """
            🔵 **Alerte : Date incohérente**
            
            Cette alerte se déclenche lorsque la date de la dépense est un week-end ou un jour férié.
            
            Dates non autorisées :
            • Week-ends : Samedi, Dimanche
            • Jours fériés tunisiens (9 jours par an)
            
            Pourquoi cette alerte ?
            • Les dépenses professionnelles sont généralement en semaine
            • Les week-ends/jours fériés nécessitent une justification
            
            Action requise :
            • Un justificatif est requis
            • Ajoutez un commentaire expliquant le contexte
            
            Exemples valides :
            • Mission à l'étranger (décalage horaire)
            • Travail le week-end (avec accord manager)
            • Dépense le jour férié pour cause professionnelle
            """;
    }

    private String getAlertesDetaillees() {
        return """
            🚨 **Système d'alertes automatiques**
            
            Le système génère 4 types d'alertes :
            
            1. 🔴 **Dépassement plafond**
               Le montant dépasse le plafond autorisé
               → Remboursement limité au plafond
            
            2. 🔴 **Facture en double**
               Une facture similaire a déjà été soumise
               → Vérification manuelle
            
            3. 🟡 **Justificatif illisible**
               La qualité du justificatif est insuffisante
               → Re-scan ou original requis
            
            4. 🔵 **Date incohérente**
               Date de dépense en week-end/jour férié
               → Justificatif requis
            
            Les alertes ne bloquent pas la soumission mais nécessitent une attention particulière.
            """;
    }

    private String getJoursFeriesDetaille() {
        return """
            📅 **Jours fériés tunisiens**
            
            Les jours fériés en Tunisie sont :
            • 1er janvier (Nouvel an)
            • 14 janvier (Fête de la Révolution)
            • 20 mars (Fête de l'Indépendance)
            • 9 avril (Fête des Martyrs)
            • 1er mai (Fête du Travail)
            • 25 juillet (Fête de la République)
            • 13 août (Fête de la Femme)
            • 15 octobre (Fête de l'Évacuation)
            • 17 décembre (Fête de la Révolution)
            
            Les week-ends : Samedi et dimanche
            
            Les jours autorisés : Lundi au vendredi (hors jours fériés)
            
            Une dépense un jour non autorisé nécessite un justificatif spécifique.
            """;
    }

    private String getPrincipesGenerauxDetaille() {
        return """
            📋 **Principes généraux de gestion des notes de frais**
            
            • Remboursement sur justificatifs ORIGINAUX
            • Délai de traitement : 5 jours ouvrés
            • Conservation des justificatifs : 6 mois
            • Devise : TND (conversion disponible en EUR, USD)
            
            Ces principes s'appliquent à toutes les notes de frais.
            """;
    }

    public boolean isStaticRulesQuestion(String question) {
        String lower = question.toLowerCase();

        List<String> staticKeywords = Arrays.asList(
                "règle", "règles", "justificatif", "format", "taille",
                "workflow", "validation", "alerte", "faq", "jour férié",
                "jours fériés", "devise", "conversion", "principes",
                "délai", "conservation", "illisible", "remboursement partiel",
                "calcul remboursement", "facture en double", "dépassement plafond"
        );

        boolean isStatic = staticKeywords.stream().anyMatch(lower::contains);
        boolean isPersonal = lower.contains("ma note") || lower.contains("mes notes") ||
                lower.contains("mon remboursement") || lower.matches(".*#\\d+.*");

        return isStatic && !isPersonal;
    }

    public String getStaticRulesOnly() {
        StringBuilder context = new StringBuilder();
        context.append(getReglesRemboursement()).append("\n\n");
        context.append(getReglesJustificatifs()).append("\n\n");
        context.append(getWorkflowDetaille()).append("\n\n");
        context.append(getAlertesDetaillees()).append("\n\n");
        context.append(getJoursFeriesDetaille());
        return context.toString();
    }
}