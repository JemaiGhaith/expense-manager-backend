package com.coralio.ai_microservice.mcp;

import com.coralio.ai_microservice.dto.DuplicateCheckResponse;
import com.coralio.ai_microservice.mcp.tools.ListFilesTool;
import com.coralio.ai_microservice.mcp.tools.ReadFileTool;
import com.coralio.ai_microservice.ollama.OllamaClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class McpServer {

    private final OllamaClient ollamaClient;
    private final ListFilesTool listFilesTool;
    private final ReadFileTool readFileTool;

    // Pool de threads pour les comparaisons parallèles
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ComparisonResult {
        private String filename;
        private String verdict;
        private double confidence;
        private String explanation;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    public static class DocumentAnalysis {
        private String filename;
        private String fullAnalysis;
        private String documentType = "Inconnu";
        private String totalAmount = "";
        private String date = "";
        private String reference = "";
        private String vendor = "";

        public String getFormattedReport() {
            return String.format("""
                Type: %s
                Montant: %s
                Date: %s
                Référence: %s
                Fournisseur: %s
                """, documentType, totalAmount, date, reference, vendor);
        }
    }

    public DuplicateCheckResponse runDuplicateDetection(String newFileBase64, String filename) {
        try {
            System.out.println("\n" + "=".repeat(80));
            System.out.println("🔍 DÉTECTION DE DOUBLONS ULTRA-PUISSANTE - Fichier: " + filename);
            System.out.println("=".repeat(80));

            // ÉTAPE 1: Scanner TOUS les fichiers du système
            McpToolResult listResult = listFilesTool.execute(new HashMap<>());
            List<String> allFiles = Arrays.stream(listResult.getContent().split("\n"))
                    .map(String::trim)
                    .filter(f -> !f.isEmpty() && !f.startsWith("Fichiers"))
                    .collect(Collectors.toList());

            System.out.println("📂 " + allFiles.size() + " fichiers trouvés au total dans TOUS les sous-dossiers");

            // ÉTAPE 2: Analyse IA approfondie du document à vérifier
            DocumentAnalysis newDocAnalysis = analyzeDocumentWithAI(newFileBase64, filename);
            System.out.println("\n📄 ANALYSE DU DOCUMENT PRINCIPAL:");
            System.out.println(newDocAnalysis.getFormattedReport());

            // ÉTAPE 3: Filtrer les fichiers potentiellement similaires
            List<String> candidatesToCompare = filterPotentialCandidates(allFiles, filename, newDocAnalysis);
            System.out.println("\n🎯 " + candidatesToCompare.size() + " candidats potentiels sélectionnés pour comparaison");

            // ÉTAPE 4: Comparaisons parallèles avec l'IA
            List<ComparisonResult> comparisonResults = compareInParallel(newDocAnalysis, candidatesToCompare);

            // ÉTAPE 5: Analyse finale des résultats
            return generateFinalVerdict(comparisonResults, newDocAnalysis, filename);

        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
            e.printStackTrace();
            return DuplicateCheckResponse.builder()
                    .isDuplicate(false)
                    .confidence(0.0)
                    .reason("Erreur technique: " + e.getMessage())
                    .detectionMethod("MCP_ULTRA_POWER")
                    .documentType("Document")
                    .build();
        }
    }

    /**
     * Analyse IA approfondie d'un document
     */
    private DocumentAnalysis analyzeDocumentWithAI(String base64Image, String filename) {
        DocumentAnalysis analysis = new DocumentAnalysis();
        analysis.setFilename(filename);

        try {
            String prompt =
                    "Tu es un expert en analyse de documents avec 20 ans d'expérience. " +
                            "Analyse ce document de façon EXTRÊMEMENT détaillée et précise.\n\n" +

                            "INSTRUCTIONS CRITIQUES:\n" +
                            "1. Observe CHAQUE pixel de l'image\n" +
                            "2. Ne JAMAIS inventer ou halluciner des informations\n" +
                            "3. Si tu n'es pas sûr à 100%, indique 'INCERTAIN'\n" +
                            "4. Extrait UNIQUEMENT ce qui est CLAIREMENT visible\n\n" +

                            "ANALYSE COMPLÈTE REQUISE:\n\n" +

                            "=== SECTION 1: IDENTIFICATION DU DOCUMENT ===\n" +
                            "- Type exact de document (Facture, Reçu, Contrat, etc.)\n" +
                            "- Langue du document\n" +
                            "- Format/Modèle visible\n\n" +

                            "=== SECTION 2: DONNÉES CHIFFRÉES ===\n" +
                            "Pour CHAQUE nombre visible, indique:\n" +
                            "  * Le nombre EXACT\n" +
                            "  * Sa signification (total, sous-total, TVA, quantité, etc.)\n" +
                            "  * Sa position dans le document\n" +
                            "  * Devise si présente\n\n" +

                            "=== SECTION 3: DATES ===\n" +
                            "Pour CHAQUE date visible:\n" +
                            "  * Date EXACTE\n" +
                            "  * Type de date (émission, échéance, livraison)\n" +
                            "  * Format\n\n" +

                            "=== SECTION 4: RÉFÉRENCES ===\n" +
                            "Liste COMPLÈTE de:\n" +
                            "  * Numéros de facture\n" +
                            "  * Numéros de client\n" +
                            "  * Numéros de commande\n" +
                            "  * Références internes\n\n" +

                            "=== SECTION 5: ENTITÉS ===\n" +
                            "  * Nom du fournisseur EXACT\n" +
                            "  * Nom du client EXACT\n" +
                            "  * Adresses si visibles\n" +
                            "  * Coordonnées\n\n" +

                            "=== SECTION 6: CARACTÉRISTIQUES UNIQUES ===\n" +
                            "  * Éléments visuels distinctifs\n" +
                            "  * Logos, tampons, signatures\n" +
                            "  * Anomalies ou marques spéciales\n\n" +

                            "=== SECTION 7: RÉSUMÉ EXÉCUTIF ===\n" +
                            "FOURNIS UN RÉSUMÉ EN 3 LIGNES MAXIMUM avec:\n" +
                            "- Type de document\n" +
                            "- Montant total (si présent)\n" +
                            "- Date principale\n" +
                            "- Numéro de référence principal\n" +
                            "- Fournisseur\n\n" +

                            "Format du résumé EXACTEMENT comme ceci:\n" +
                            "RÉSUMÉ|TYPE|MONTANT|DATE|RÉFÉRENCE|FOURNISSEUR\n" +
                            "Exemple: RÉSUMÉ|Facture|1250.50€|15/03/2024|FAC-2024-001|Entreprise ABC\n\n" +

                            "Réponds en français de façon EXTÊMEMENT détaillée.";

            String response = ollamaClient.chatWithImages(null, prompt, List.of(base64Image));
            analysis.setFullAnalysis(response);

            // Parser le résumé
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.startsWith("RÉSUMÉ|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 6) {
                        analysis.setDocumentType(parts[1]);
                        analysis.setTotalAmount(parts[2]);
                        analysis.setDate(parts[3]);
                        analysis.setReference(parts[4]);
                        analysis.setVendor(parts[5]);
                    }
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("⚠️ Erreur analyse IA: " + e.getMessage());
            analysis.setDocumentType(detectTypeFromFilename(filename));
        }

        return analysis;
    }

    /**
     * Compare deux documents avec l'IA
     */
    private ComparisonResult compareDocumentsWithAI(DocumentAnalysis doc1, DocumentAnalysis doc2,
                                                    String base64Img1, String base64Img2) {
        try {
            String prompt =
                    "Tu es un expert judiciaire en analyse de documents. " +
                            "Ta mission: COMPARER ces deux documents avec une PRÉCISION ABSOLUE.\n\n" +

                            "⚠️ RÈGLES STRICTES ⚠️\n" +
                            "1. Tu dois être CERTAIN à 100% pour déclarer un doublon\n" +
                            "2. Le moindre doute = NON DOUBLON\n" +
                            "3. Observe les DEUX images pixel par pixel\n" +
                            "4. Compare CHAQUE détail minutieusement\n\n" +

                            "DOCUMENT 1: " + doc1.getFilename() + "\n" +
                            "DOCUMENT 2: " + doc2.getFilename() + "\n\n" +

                            "ANALYSE COMPARATIVE COMPLÈTE:\n\n" +

                            "1️⃣ COMPARAISON DES MONTANTS\n" +
                            "- Montant document 1: " + doc1.getTotalAmount() + "\n" +
                            "- Montant document 2: " + doc2.getTotalAmount() + "\n" +
                            "- Sont-ils IDENTIQUES (mêmes chiffres, même devise)?\n\n" +

                            "2️⃣ COMPARAISON DES RÉFÉRENCES\n" +
                            "- Référence document 1: " + doc1.getReference() + "\n" +
                            "- Référence document 2: " + doc2.getReference() + "\n" +
                            "- Sont-elles EXACTEMENT identiques?\n\n" +

                            "3️⃣ COMPARAISON DES DATES\n" +
                            "- Date document 1: " + doc1.getDate() + "\n" +
                            "- Date document 2: " + doc2.getDate() + "\n" +
                            "- Sont-elles EXACTEMENT identiques?\n\n" +

                            "4️⃣ COMPARAISON DES FOURNISSEURS\n" +
                            "- Fournisseur document 1: " + doc1.getVendor() + "\n" +
                            "- Fournisseur document 2: " + doc2.getVendor() + "\n" +
                            "- Sont-ils EXACTEMENT identiques?\n\n" +

                            "5️⃣ COMPARAISON VISUELLE APPROFONDIE\n" +
                            "- Mise en page identique?\n" +
                            "- Mêmes logos/images?\n" +
                            "- Même police de caractères?\n" +
                            "- Même disposition des éléments?\n" +
                            "- Même tampon/signature?\n" +
                            "- Même taille/apparence générale?\n\n" +

                            "6️⃣ ANALYSE DES DIFFÉRENCES POTENTIELLES\n" +
                            "- Y a-t-il la MOINDRE différence visible?\n" +
                            "- Si OUI, liste-les TOUTES précisément\n\n" +

                            "🔴 DÉCISION FINALE (FORMAT STRICT)\n" +
                            "DOUBLON? Réponds UNIQUEMENT par:\n" +
                            "- 'OUI|100%|explication' si c'est un doublon CERTAIN\n" +
                            "- 'NON|0%|explication' si ce n'est PAS un doublon\n" +
                            "- 'DOUTE|XX%|explication' si tu as un doute avec niveau de confiance\n\n" +

                            "Analyse maintenant avec une attention EXTREME:";

            // Envoyer les DEUX images pour comparaison directe
            String response = ollamaClient.chatWithImages(null, prompt, List.of(base64Img1, base64Img2));

            return parseComparisonResponse(response, doc2.getFilename());

        } catch (Exception e) {
            System.err.println("⚠️ Erreur comparaison IA: " + e.getMessage());
            return new ComparisonResult(doc2.getFilename(), "ERREUR", 0.0, e.getMessage());
        }
    }

    /**
     * Parse la réponse de comparaison
     */
    private ComparisonResult parseComparisonResponse(String response, String filename) {
        String[] lines = response.split("\n");
        String verdict = "DOUTE";
        double confidence = 0.0;
        String explanation = "";

        for (String line : lines) {
            if (line.contains("OUI|") || line.startsWith("OUI|")) {
                verdict = "DOUBLON";
                String[] parts = line.split("\\|");
                if (parts.length > 1) {
                    try {
                        String confStr = parts[1].replace("%", "");
                        confidence = Double.parseDouble(confStr) / 100;
                    } catch (Exception e) {
                        confidence = 0.95;
                    }
                }
                if (parts.length > 2) {
                    explanation = parts[2];
                }
            } else if (line.contains("NON|") || line.startsWith("NON|")) {
                verdict = "NON_DOUBLON";
                confidence = 0.0;
                String[] parts = line.split("\\|");
                if (parts.length > 2) {
                    explanation = parts[2];
                }
            } else if (line.contains("DOUTE|") || line.startsWith("DOUTE|")) {
                verdict = "DOUTE";
                String[] parts = line.split("\\|");
                if (parts.length > 1) {
                    try {
                        String confStr = parts[1].replace("%", "");
                        confidence = Double.parseDouble(confStr) / 100;
                    } catch (Exception e) {
                        confidence = 0.5;
                    }
                }
                if (parts.length > 2) {
                    explanation = parts[2];
                }
            }
        }

        return new ComparisonResult(filename, verdict, confidence, explanation);
    }

    /**
     * Filtre les candidats potentiels basé sur l'analyse
     */
    private List<String> filterPotentialCandidates(List<String> allFiles, String currentFile,
                                                   DocumentAnalysis analysis) {
        // Exclure le fichier courant
        return allFiles.stream()
                .filter(f -> !f.contains(currentFile) && !f.equals(currentFile))
                .limit(20) // Limite pour performance
                .collect(Collectors.toList());
    }

    /**
     * Compare en parallèle avec plusieurs candidats
     */
    private List<ComparisonResult> compareInParallel(DocumentAnalysis mainDoc,
                                                     List<String> candidates) {
        List<ComparisonResult> results = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Lire l'image principale une seule fois
        Map<String, String> mainParams = new HashMap<>();
        mainParams.put("file_path", mainDoc.getFilename());
        McpToolResult mainImageResult = readFileTool.execute(mainParams);

        if (!mainImageResult.isSuccess()) {
            System.err.println("❌ Impossible de lire l'image principale: " + mainDoc.getFilename());
            return results;
        }

        String mainBase64 = mainImageResult.getContent();

        // Lancer les comparaisons en parallèle
        for (String candidate : candidates) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // Lire l'image candidate
                    Map<String, String> params = new HashMap<>();
                    params.put("file_path", candidate);
                    McpToolResult imgResult = readFileTool.execute(params);

                    if (!imgResult.isSuccess()) {
                        results.add(new ComparisonResult(candidate, "ERREUR_LECTURE", 0.0,
                                "Impossible de lire le fichier"));
                        return;
                    }

                    // Analyser le document candidat
                    DocumentAnalysis candidateAnalysis = analyzeDocumentWithAI(
                            imgResult.getContent(), candidate);

                    // Comparer avec l'IA
                    ComparisonResult compResult = compareDocumentsWithAI(
                            mainDoc, candidateAnalysis, mainBase64, imgResult.getContent());

                    results.add(compResult);

                } catch (Exception e) {
                    results.add(new ComparisonResult(candidate, "ERREUR", 0.0, e.getMessage()));
                }
            }, executorService);

            futures.add(future);
        }

        // Attendre tous les résultats
        for (CompletableFuture<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                System.err.println("⚠️ Erreur future: " + e.getMessage());
            }
        }

        return results;
    }

    /**
     * Génère le verdict final basé sur TOUTES les comparaisons
     */
    private DuplicateCheckResponse generateFinalVerdict(List<ComparisonResult> results,
                                                        DocumentAnalysis mainDoc,
                                                        String filename) {

        System.out.println("\n" + "=".repeat(80));
        System.out.println("📊 ANALYSE FINALE DES RÉSULTATS");
        System.out.println("=".repeat(80));

        // Trier par confiance
        results.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));

        // Afficher les 5 meilleures correspondances
        System.out.println("\n🏆 TOP 5 CORRESPONDANCES:");
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            ComparisonResult r = results.get(i);
            System.out.printf("%d. %s - %s (confiance: %.1f%%) - %s\n",
                    i+1, r.getFilename(), r.getVerdict(), r.getConfidence()*100, r.getExplanation());
        }

        // Trouver le meilleur doublon potentiel
        ComparisonResult bestMatch = results.stream()
                .filter(r -> "DOUBLON".equals(r.getVerdict()))
                .findFirst()
                .orElse(null);

        if (bestMatch != null && bestMatch.getConfidence() >= 0.95) {
            System.out.println("\n✅ DOUBLON CERTAIN TROUVÉ!");
            return DuplicateCheckResponse.builder()
                    .isDuplicate(true)
                    .confidence(bestMatch.getConfidence())
                    .matchedFile(bestMatch.getFilename())
                    .reason(bestMatch.getExplanation())
                    .detectionMethod("IA_ULTRA_POWER_V1")
                    .documentType(mainDoc.getDocumentType())
                    .build();
        }

        // Vérifier les doutes avec haute confiance
        ComparisonResult bestDoubt = results.stream()
                .filter(r -> "DOUTE".equals(r.getVerdict()) && r.getConfidence() >= 0.8)
                .findFirst()
                .orElse(null);

        if (bestDoubt != null) {
            System.out.println("\n⚠️ DOUTE AVEC HAUTE CONFIANCE - Vérification supplémentaire...");

            // Double vérification avec un prompt encore plus strict
            return performDoubleCheck(mainDoc, bestDoubt, filename);
        }

        System.out.println("\n❌ AUCUN DOUBLON TROUVÉ");
        return DuplicateCheckResponse.builder()
                .isDuplicate(false)
                .confidence(results.isEmpty() ? 0.0 : results.get(0).getConfidence())
                .matchedFile(results.isEmpty() ? null : results.get(0).getFilename())
                .reason(results.isEmpty() ? "Aucun fichier à comparer" :
                        "Aucun doublon certain trouvé après analyse approfondie")
                .detectionMethod("IA_ULTRA_POWER_V1")
                .documentType(mainDoc.getDocumentType())
                .build();
    }

    /**
     * Double vérification pour les cas douteux
     */
    private DuplicateCheckResponse performDoubleCheck(DocumentAnalysis mainDoc,
                                                      ComparisonResult doubt,
                                                      String filename) {
        try {
            String prompt =
                    "🚨 ULTIME VÉRIFICATION JUDICIAIRE 🚨\n\n" +
                            "Tu dois prendre une décision FINALE et IRRÉVOCABLE.\n" +
                            "Document 1: " + mainDoc.getFilename() + "\n" +
                            "Document 2: " + doubt.getFilename() + "\n\n" +

                            "RAPPEL DES DONNÉES EXTRAITES:\n" +
                            "Document 1 - Montant: " + mainDoc.getTotalAmount() +
                            ", Réf: " + mainDoc.getReference() +
                            ", Date: " + mainDoc.getDate() +
                            ", Fournisseur: " + mainDoc.getVendor() + "\n" +
                            "Document 2 - Doute: " + doubt.getExplanation() + "\n\n" +

                            "⚠️ RÈGLE D'OR: Si le moindre élément diffère → NON DOUBLON\n" +
                            "Pour être DOUBLON, TOUT doit être IDENTIQUE:\n" +
                            "✓ Montant exact\n" +
                            "✓ Date exacte\n" +
                            "✓ Référence exacte\n" +
                            "✓ Fournisseur exact\n" +
                            "✓ Mise en page identique\n\n" +

                            "Réponds UNIQUEMENT par:\n" +
                            "CERTAIN_DOUBLON|confiance|explication\n" +
                            "ou\n" +
                            "CERTAIN_NON_DOUBLON|0|explication";

            String response = ollamaClient.chatWithImages(null, prompt,
                    List.of(mainDoc.getFullAnalysis(), doubt.getExplanation()));

            if (response.contains("CERTAIN_DOUBLON")) {
                String[] parts = response.split("\\|");
                double confidence = parts.length > 1 ?
                        Double.parseDouble(parts[1].replace("%", "")) / 100 : 0.99;

                return DuplicateCheckResponse.builder()
                        .isDuplicate(true)
                        .confidence(confidence)
                        .matchedFile(doubt.getFilename())
                        .reason(parts.length > 2 ? parts[2] : "Confirmé après double vérification")
                        .detectionMethod("IA_ULTRA_POWER_V2")
                        .documentType(mainDoc.getDocumentType())
                        .build();
            }

        } catch (Exception e) {
            System.err.println("⚠️ Erreur double vérif: " + e.getMessage());
        }

        return DuplicateCheckResponse.builder()
                .isDuplicate(false)
                .confidence(doubt.getConfidence())
                .matchedFile(doubt.getFilename())
                .reason("Doute non confirmé après double vérification")
                .detectionMethod("IA_ULTRA_POWER_V1")
                .documentType(mainDoc.getDocumentType())
                .build();
    }

    /**
     * Détecte le type de document à partir du nom de fichier (fallback)
     */
    private String detectTypeFromFilename(String filename) {
        String lower = filename.toLowerCase();
        if (lower.contains("facture") || lower.contains("invoice")) return "Facture";
        if (lower.contains("reçu") || lower.contains("recu") || lower.contains("receipt")) return "Reçu";
        if (lower.contains("contrat") || lower.contains("contract")) return "Contrat";
        if (lower.contains("accord")) return "Accord";
        if (lower.contains("cv") || lower.contains("resume")) return "CV";
        return "Document";
    }
}