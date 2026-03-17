package com.coralio.chatbotmicroservice.config;

import com.coralio.chatbotmicroservice.model.Currency;
import com.coralio.chatbotmicroservice.model.*;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;

@Component
public class RulesConfig {

    private List<CategoryRule> categories;
    private List<AlertRule> alertRules;
    private List<GeneralRule> generalRules;
    private List<WorkflowStep> workflowSteps;
    private List<FAQ> faqs;
    private List<Currency> currencies;

    @PostConstruct
    public void init() {
        initCurrencies();
        initCategories();
        initAlertRules();
        initGeneralRules();
        initWorkflow();
        initFAQs();
    }

    private void initCurrencies() {
        currencies = Arrays.asList(
                new Currency("TND", "DT", "Dinar Tunisien", 1.0),
                new Currency("EUR", "€", "Euro", 0.30),
                new Currency("USD", "$", "Dollar US", 0.32),
                new Currency("GBP", "£", "Livre Sterling", 0.26)
        );
    }

    private void initCategories() {
        categories = Arrays.asList(
                CategoryRule.builder()
                        .id(1L)
                        .name("Transport")
                        .plafond(new BigDecimal("200"))
                        .active(true)
                        .icon("car-front")
                        .fields(Arrays.asList(
                                new CategoryRule.CategoryField("depart", "Départ", "TEXT", true, "Ville de départ"),
                                new CategoryRule.CategoryField("destination", "Destination", "TEXT", true, "Ville d'arrivée"),
                                new CategoryRule.CategoryField("transport_type", "Type de transport", "SELECT", true, "Train, Avion, Taxi"),
                                new CategoryRule.CategoryField("date_depart", "Date de départ", "DATE", true, null)
                        ))
                        .specialRules(Arrays.asList(
                                "Ville de départ/destination requises",
                                "Justificatif obligatoire > 50 TND"
                        ))
                        .build(),

                CategoryRule.builder()
                        .id(2L)
                        .name("Hébergement")
                        .plafond(new BigDecimal("150"))
                        .active(true)
                        .icon("house-door")
                        .fields(Arrays.asList(
                                new CategoryRule.CategoryField("hotel_name", "Nom de l'hôtel", "TEXT", true, null),
                                new CategoryRule.CategoryField("nombre_nuits", "Nombre de nuits", "NUMBER", true, null),
                                new CategoryRule.CategoryField("date_arrivee", "Date d'arrivée", "DATE", true, null),
                                new CategoryRule.CategoryField("date_depart", "Date de départ", "DATE", true, null)
                        ))
                        .specialRules(Arrays.asList(
                                "Justificatif obligatoire avec dates de séjour",
                                "Plafond par nuit : 150 TND"
                        ))
                        .build(),

                CategoryRule.builder()
                        .id(3L)
                        .name("Restauration")
                        .plafond(new BigDecimal("80"))
                        .active(true)
                        .icon("cup-hot")
                        .fields(Arrays.asList(
                                new CategoryRule.CategoryField("repas_type", "Type de repas", "SELECT", true, "Déjeuner, Dîner"),
                                new CategoryRule.CategoryField("nombre_personnes", "Nombre de personnes", "NUMBER", true, null),
                                new CategoryRule.CategoryField("restaurant_name", "Nom du restaurant", "TEXT", false, null),
                                new CategoryRule.CategoryField("date_repas", "Date du repas", "DATE", true, null)
                        ))
                        .specialRules(Arrays.asList(
                                "Nombre de convives à préciser",
                                "Plafond par personne : 40 TND"
                        ))
                        .build(),

                CategoryRule.builder()
                        .id(4L)
                        .name("Carburant")
                        .plafond(new BigDecimal("120"))
                        .active(true)
                        .icon("fuel-pump")
                        .fields(Arrays.asList(
                                new CategoryRule.CategoryField("kilometrage", "Kilométrage", "NUMBER", true, "Distance parcourue en km"),
                                new CategoryRule.CategoryField("vehicule", "Véhicule", "TEXT", true, "Immatriculation ou modèle"),
                                new CategoryRule.CategoryField("litres", "Litres", "NUMBER", true, "Quantité de carburant"),
                                new CategoryRule.CategoryField("prix_litre", "Prix au litre", "NUMBER", true, null)
                        ))
                        .specialRules(Arrays.asList(
                                "Frais kilométriques: 0.3 TND/km",
                                "Justificatif de la station-service requis"
                        ))
                        .build(),

                CategoryRule.builder()
                        .id(5L)
                        .name("Frais professionnels")
                        .plafond(new BigDecimal("300"))
                        .active(true)
                        .icon("briefcase")
                        .fields(Arrays.asList(
                                new CategoryRule.CategoryField("detail", "Détail", "TEXTAREA", true, "Description détaillée"),
                                new CategoryRule.CategoryField("fournisseur", "Fournisseur", "TEXT", true, null),
                                new CategoryRule.CategoryField("reference", "Référence", "TEXT", false, "Numéro de facture")
                        ))
                        .specialRules(Arrays.asList(
                                "Justificatif détaillé obligatoire",
                                "Validation manager préalable requise pour montant > 500 TND"
                        ))
                        .build(),

                CategoryRule.builder()
                        .id(6L)
                        .name("Autres")
                        .plafond(new BigDecimal("100"))
                        .active(true)
                        .icon("three-dots")
                        .fields(Arrays.asList(
                                new CategoryRule.CategoryField("description", "Description", "TEXT", true, null),
                                new CategoryRule.CategoryField("justificatif", "Justificatif", "FILE", true, "Document justificatif")
                        ))
                        .specialRules(Collections.emptyList())
                        .build()
        );
    }

    private void initAlertRules() {
        alertRules = Arrays.asList(
                AlertRule.builder()
                        .name("Dépassement plafond")
                        .code("ALERTE_MONTANT_PLAFOND")
                        .description("Le montant saisi dépasse le plafond autorisé pour cette catégorie")
                        .icon("exclamation-triangle")
                        .color("#ffc107")
                        .severity("warning")
                        .action("Remboursement partiel automatique")
                        .build(),

                AlertRule.builder()
                        .name("Facture en double")
                        .code("ALERTE_FACTURE_DOUBLE")
                        .description("Une facture similaire a déjà été soumise récemment")
                        .icon("files")
                        .color("#dc3545")
                        .severity("danger")
                        .action("Vérification manuelle")
                        .build(),

                AlertRule.builder()
                        .name("Justificatif illisible")
                        .code("ALERTE_JUSTIFICATIF_ILLISIBLE")
                        .description("La qualité du justificatif est insuffisante pour l'OCR")
                        .icon("file-earmark-image")
                        .color("#fd7e14")
                        .severity("warning")
                        .action("Re-scan ou original")
                        .build(),

                AlertRule.builder()
                        .name("Date incohérente")
                        .code("ALERTE_DATE_INCOHERENTE")
                        .description("La date de la dépense est incohérente (week-end/jour férié)")
                        .icon("calendar2-x")
                        .color("#6f42c1")
                        .severity("info")
                        .action("Justificatif requis")
                        .build()
        );
    }

    private void initGeneralRules() {
        generalRules = Arrays.asList(
                new GeneralRule("Remboursement sur justificatifs originaux", "Principes généraux"),
                new GeneralRule("Délai de traitement : 5 jours ouvrés", "Principes généraux"),
                new GeneralRule("Conservation des justificatifs : 6 mois", "Principes généraux"),
                new GeneralRule("Devise : TND (conversion possible)", "Principes généraux"),
                new GeneralRule("Formats acceptés : PDF, JPG, PNG, DOC, DOCX", "Règles justificatifs"),
                new GeneralRule("Taille max : 10 Mo par fichier", "Règles justificatifs"),
                new GeneralRule("Un justificatif par ligne de frais", "Règles justificatifs"),
                new GeneralRule("Justificatifs illisibles → ALERTE", "Règles justificatifs")
        );
    }

    private void initWorkflow() {
        workflowSteps = Arrays.asList(
                new WorkflowStep(1, "Brouillon", "Saisie par l'employé", "EMPLOYEE"),
                new WorkflowStep(2, "Soumise", "En attente validation manager", "EMPLOYEE"),
                new WorkflowStep(3, "Validée / Refusée", "Décision du manager", "MANAGER"),
                new WorkflowStep(4, "Contrôlée", "Vérification admin", "ADMIN"),
                new WorkflowStep(5, "Remboursée", "Paiement effectué", "ADMIN")
        );
    }

    private void initFAQs() {
        faqs = Arrays.asList(
                new FAQ(
                        "Comment sont calculés les remboursements partiels ?",
                        "Si votre dépense dépasse le plafond de la catégorie, vous serez remboursé uniquement jusqu'à concurrence du plafond. La différence reste à votre charge."
                ),
                new FAQ(
                        "Que faire si mon justificatif est illisible ?",
                        "Le système générera une alerte 'JUSTIFICATIF_ILLISIBLE'. Vous pouvez quand même soumettre la note, mais elle sera examinée manuellement par l'administrateur."
                ),
                new FAQ(
                        "Puis-je être remboursé sans justificatif ?",
                        "Non, un justificatif valide est obligatoire pour chaque ligne de frais. Sans justificatif, la note sera refusée."
                ),
                new FAQ(
                        "Combien de temps faut-il pour être remboursé ?",
                        "Le délai standard est de 5 jours ouvrables après validation complète (manager + admin)."
                ),
                new FAQ(
                        "Quels sont les jours fériés exclus ?",
                        "Les dépenses ne sont pas autorisées les jours fériés tunisiens : 1er janvier, 14 janvier, 20 mars, 9 avril, 1er mai, 25 juillet, 13 août, 15 octobre, 17 décembre."
                )
        );
    }

    // Getters
    public List<CategoryRule> getCategories() { return categories; }
    public List<AlertRule> getAlertRules() { return alertRules; }
    public List<GeneralRule> getGeneralRules() { return generalRules; }
    public List<WorkflowStep> getWorkflowSteps() { return workflowSteps; }
    public List<FAQ> getFaqs() { return faqs; }
    public List<Currency> getCurrencies() { return currencies; }

    public CategoryRule getCategoryByName(String name) {
        return categories.stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public List<GeneralRule> getGeneralRulesByCategory(String category) {
        return generalRules.stream()
                .filter(r -> r.getCategory().equals(category))
                .toList();
    }

    public BigDecimal getMaxPlafond() {
        return categories.stream()
                .map(CategoryRule::getPlafond)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.valueOf(1000));
    }

    public BigDecimal getAveragePlafond() {
        return categories.stream()
                .map(CategoryRule::getPlafond)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(categories.size()), BigDecimal.ROUND_HALF_UP);
    }
}