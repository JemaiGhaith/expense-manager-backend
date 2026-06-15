package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.entities.*;
import com.coralio.expense_management_microservice.repos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ExpenseProcessingService {

    @Autowired
    private ExpenseExtractionRepository extractionRepository;

    @Autowired
    private ExpenseLineRepository lineRepository;

    @Autowired
    private ExpenseDuplicateRepository duplicateRepository;

    @Autowired
    private ExpenseNoteRepository noteRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ExpenseNoteExtractionRepository noteExtractionRepository;

    @Autowired
    private CategoryService categoryService;
    // ✅ ADDED: inject ExpenseService to call full analysis
    @Autowired
    @Lazy
    private ExpenseService expenseService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadsDir;
    @Value("${ai.faiss.add.url:http://localhost:9000/add-to-index}")
    private String faissAddUrl;

    @Value("${ai.validate.from.json.url:http://localhost:8085/api/ai/validate-from-json}")
    private String validateFromJsonUrl;

    @Value("${ai.check.duplicate.url:http://localhost:8085/api/ai/check-duplicate-from-text}")
    private String checkDuplicateUrl;
    @Value("${ai.accord.analyze.url:http://localhost:9000/analyze-for-accord}")
    private String accordAnalyzeUrl;

    @Value("${ai.faiss.add.accord.url:http://localhost:9000/add-accord-to-index}")
    private String faissAddAccordUrl;
    @Value("${ai.anomaly.url:http://localhost:9000/detect-anomaly-ai}")
    private String anomalyUrl;
    // Au début de la classe, avec les autres @Value
    @Value("${python.ai.url:http://localhost:9000}")
    private String pythonBaseUrl;
    // Helper to add a document to FAISS
    private void addToFaissIndex(String text, String filename, String absolutePath) {
        try {
            String url = faissAddUrl;
            Map<String, Object> body = Map.of(
                    "text", text,
                    "filename", filename,
                    "filepath", absolutePath
            );
            restTemplate.postForObject(url, body, Map.class);
            log.info("✅ Added to FAISS: {} -> {}", filename, absolutePath);
        } catch (Exception e) {
            log.error("❌ FAISS error: {}", e.getMessage());
        }
    }

    @Async
    @Transactional
    public void processAfterSubmission(Long noteId, List<ExpenseLine> lines) {
        log.info("=== START async processing for note {}", noteId);
        ExpenseNote note = noteRepository.findById(noteId).orElse(null);
        if (note == null) {
            log.error("Note {} not found", noteId);
            return;
        }
        String employeeId = note.getEmployeeId();

        // 1. Process each expense line (justificatif)
        for (ExpenseLine line : lines) {
            try {
                // Ensure OCR is available (calls /analyze if missing)
                String ocrText = ensureLineExtraction(line, employeeId, noteId);

                // --- INDEXATION SYSTÉMATIQUE ---
                Path fullPath = Paths.get(uploadsDir, employeeId, line.getJustificatifPath()).toAbsolutePath();
                String absolutePath = fullPath.toString();
                addToFaissIndex(ocrText, line.getJustificatifPath(), absolutePath);
                // -----------------------------

                // Validation (via JSON)
                Optional<ExpenseExtraction> optExt = extractionRepository.findByExpenseLineId(line.getId());
                if (optExt.isPresent() && optExt.get().getExtractedJson() != null) {
                    Map<String, Object> extractedJson = optExt.get().getExtractedJson();
                    try {
                        Map<String, Object> validationResult = restTemplate.postForObject(
                                validateFromJsonUrl,
                                extractedJson,
                                Map.class
                        );
                        if (validationResult != null) {
                            Boolean isValid = (Boolean) validationResult.getOrDefault("is_accurate", false);
                            Double confidence = (Double) validationResult.getOrDefault("confidence", 0.0);
                            @SuppressWarnings("unchecked")
                            List<String> issues = (List<String>) validationResult.getOrDefault("issues", List.of());
                            String issuesStr = String.join("; ", issues);
                            line.setValidationValid(isValid);
                            line.setValidationScore(confidence);
                            line.setValidationIssues(issuesStr);
                            lineRepository.save(line);
                            log.info("✅ Validation saved for line {}", line.getId());
                        }
                    } catch (Exception e) {
                        log.error("Validation error line {}: {}", line.getId(), e.getMessage());
                    }
                }

                // Duplicate detection
                String filepath = fullPath.toString();
                String excludePath = filepath;
                Map<String, String> payload = Map.of(
                        "ocrText", ocrText,
                        "filepath", filepath,
                        "excludePath", excludePath
                );
                Map<String, Object> result = restTemplate.postForObject(
                        checkDuplicateUrl + "?employeeId=" + employeeId,
                        payload,
                        Map.class
                );
                if (result != null) {
                    boolean duplicate = (boolean) result.getOrDefault("duplicate", false);
                    double score = Double.parseDouble(result.get("score").toString());
                    String matchedPath = (String) result.get("matchedPath");
                    if (matchedPath == null) matchedPath = (String) result.get("path");

                    // ✅ Always fetch a managed instance of the line
                    ExpenseLine managedLine = lineRepository.findById(line.getId()).orElse(null);
                    if (managedLine == null) {
                        log.error("Line {} not found, cannot update duplicate_detected", line.getId());
                        continue;
                    }

                    if (duplicate && matchedPath != null) {
                        boolean alreadyExists = duplicateRepository.existsByExpenseNoteIdAndExpenseLineIdAndUploadedFile(
                                noteId, line.getId(), line.getJustificatifPath());
                        if (!alreadyExists) {
                            String finalPath = matchedPath.length() > 255 ? matchedPath.substring(0, 255) : matchedPath;
                            ExpenseDuplicate dup = new ExpenseDuplicate();
                            dup.setExpenseNoteId(noteId);
                            dup.setExpenseLineId(line.getId());
                            dup.setUploadedFile(line.getJustificatifPath());
                            dup.setDuplicateFile(finalPath);
                            dup.setSimilarity(score);
                            duplicateRepository.save(dup);
                            log.info("✅ Duplicate saved for line {}", line.getId());
                        } else {
                            log.info("Duplicate already exists for line {}, skipping duplicate record insertion", line.getId());
                        }

                        // ✅ ALWAYS update the expense line flag
                        managedLine.setDuplicateDetected(true);
                        managedLine.setDuplicateScore(score);
                        if (matchedPath != null) {
                            String finalPath = matchedPath.length() > 255 ? matchedPath.substring(0, 255) : matchedPath;
                            managedLine.setDuplicateMatchedFile(finalPath);
                        }
                        lineRepository.save(managedLine);
                    } else {
                        managedLine.setDuplicateDetected(false);
                        lineRepository.save(managedLine);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing line {}: {}", line.getId(), e.getMessage(), e);
            }
        }

        // 2. Process accord (if present)
        if (note.getAccordPath() != null && !note.getAccordPath().isEmpty()) {
            try {
                String accordOcrText = ensureAccordExtraction(noteId, employeeId, note.getAccordPath());

                // Indexation systématique
                Path accordFullPath = Paths.get(uploadsDir, employeeId, note.getAccordPath()).toAbsolutePath();
                String absolutePath = accordFullPath.toString();
                String accordFileName = Paths.get(note.getAccordPath()).getFileName().toString();
                addToFaissIndex(accordOcrText, accordFileName, absolutePath);

                // Duplicate detection for accord
                String filepath = absolutePath;
                String excludePath = filepath;
                Map<String, String> payload = Map.of(
                        "ocrText", accordOcrText,
                        "filepath", filepath,
                        "excludePath", excludePath
                );
                Map<String, Object> result = restTemplate.postForObject(
                        checkDuplicateUrl + "?employeeId=" + employeeId,
                        payload,
                        Map.class
                );
                if (result != null) {
                    boolean duplicate = (boolean) result.getOrDefault("duplicate", false);
                    double score = Double.parseDouble(result.get("score").toString());
                    String matchedPath = (String) result.get("matchedPath");
                    if (matchedPath == null) matchedPath = (String) result.get("path");

                    if (duplicate && matchedPath != null) {
                        // Vérifier doublon existant pour l'accord (expense_line_id = null)
                        boolean alreadyExists = duplicateRepository.existsByExpenseNoteIdAndExpenseLineIdIsNullAndUploadedFile(
                                noteId, note.getAccordPath());
                        if (!alreadyExists) {
                            String finalPath = matchedPath.length() > 255 ? matchedPath.substring(0, 255) : matchedPath;
                            ExpenseDuplicate dup = new ExpenseDuplicate();
                            dup.setExpenseNoteId(noteId);
                            dup.setExpenseLineId(null);
                            dup.setUploadedFile(note.getAccordPath());
                            dup.setDuplicateFile(finalPath);
                            dup.setSimilarity(score);
                            duplicateRepository.save(dup);
                            log.info("✅ Accord duplicate saved for note {}", noteId);
                        } else {
                            log.info("Accord duplicate already exists for note {}, skipping", noteId);
                        }
                    } else {
                        log.info("No duplicate found for accord of note {}", noteId);
                    }
                }

                // ========== ✅ ADDED: Full AI analysis (anomalies) ==========
                // Call the existing method from ExpenseService (no changes needed there)
                String analysisResult = expenseService.callFullAnalysis(note, lines, note.getAccordPath());
                if (analysisResult != null) {
                    note.setAiAnalysisResult(analysisResult);
                    noteRepository.save(note);
                    log.info("✅ Full AI analysis saved for note {}", noteId);
                } else {
                    log.warn("Full analysis returned null for note {}", noteId);
                }
                // =========================================================

            } catch (Exception e) {
                log.error("Error processing accord for note {}: {}", noteId, e.getMessage(), e);
            }
        }

        log.info("=== END async processing for note {}", noteId);
    }

    // ------------------------------------------------------------------------
    // Helper methods for extraction with fallback (no indexing here)
    // ------------------------------------------------------------------------

    private String ensureLineExtraction(ExpenseLine line, String employeeId, Long expenseNoteId) throws Exception {
        Optional<ExpenseExtraction> existing = extractionRepository.findByExpenseLineId(line.getId());
        Map<String, Object> structured = null;

        if (existing.isPresent() && existing.get().getOcrText() != null) {
            structured = existing.get().getExtractedJson();
            log.info("📄 Extraction existante trouvée pour ligne {}", line.getId());
        } else {
            Path fullPath = Paths.get(uploadsDir, employeeId, line.getJustificatifPath()).toAbsolutePath();
            if (!Files.exists(fullPath)) {
                throw new RuntimeException("File not found: " + fullPath);
            }
            byte[] fileBytes = Files.readAllBytes(fullPath);

            String analyzeUrl = faissAddUrl.replace("/add-to-index", "/analyze");
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(fileBytes) {
                @Override public String getFilename() { return fullPath.getFileName().toString(); }
            });
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            Map<String, Object> response = restTemplate.postForObject(analyzeUrl, request, Map.class);
            String ocrText = (String) response.get("ocr_text");
            structured = (Map<String, Object>) response.get("structured");

            ExpenseExtraction extraction = ExpenseExtraction.builder()
                    .expenseLineId(line.getId())
                    .ocrText(ocrText)
                    .extractedJson(structured)
                    .extractionVersion("v1")
                    .build();
            extractionRepository.save(extraction);
            log.info("✅ Nouvelle extraction sauvegardée pour ligne {}", line.getId());
        }

        // ========== 🔥 AJOUTER LA COMPARAISON ICI ==========
        if (structured != null && !structured.isEmpty()) {
            try {
                Map<String, Object> formData = buildFormDataFromLine(line);
                log.info("🔍 Comparaison ligne {} - Invoice data present, form data: {}",
                        line.getId(), formData.keySet());

                Map<String, Object> comparisonResult = compareLineWithInvoice(structured, formData, 0);

                saveComparisonResult(line, comparisonResult);

                // Si incohérence détectée, logguer
                Boolean isConsistent = (Boolean) comparisonResult.getOrDefault("is_consistent", false);
                if (!isConsistent) {
                    log.warn("⚠️ Incohérence détectée pour ligne {}: {}", line.getId(), comparisonResult.get("summary"));
                } else {
                    log.info("✅ Ligne {} cohérente avec la facture", line.getId());
                }
            } catch (Exception e) {
                log.error("❌ Erreur lors de la comparaison ligne {}: {}", line.getId(), e.getMessage(), e);
            }
        } else {
            log.warn("⚠️ Pas de données structurées pour la ligne {}, comparaison ignorée", line.getId());
        }
        // ===================================================

        String ocrText = existing.isPresent() && existing.get().getOcrText() != null ?
                existing.get().getOcrText() : "Extracted";

        return ocrText;
    }

    private String ensureAccordExtraction(Long expenseNoteId, String employeeId, String accordPath) throws Exception {
        Optional<ExpenseNoteExtraction> existing = noteExtractionRepository.findByExpenseNoteId(expenseNoteId);
        if (existing.isPresent() && existing.get().getOcrText() != null) {
            return existing.get().getOcrText();
        }

        Path fullPath = Paths.get(uploadsDir, employeeId, accordPath).toAbsolutePath();
        if (!Files.exists(fullPath)) {
            throw new RuntimeException("Accord file not found: " + fullPath);
        }
        byte[] fileBytes = Files.readAllBytes(fullPath);

        // Appel à /analyze-for-accord
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override public String getFilename() { return fullPath.getFileName().toString(); }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        Map<String, Object> response = restTemplate.postForObject(accordAnalyzeUrl, request, Map.class);
        String ocrText = (String) response.get("ocr_text");
        Map<String, Object> structured = (Map<String, Object>) response.get("structured");

        // Sauvegarde en base
        ExpenseNoteExtraction extraction = ExpenseNoteExtraction.builder()
                .expenseNoteId(expenseNoteId)
                .ocrText(ocrText)
                .extractedJson(structured)
                .extractionVersion("v1")
                .build();
        noteExtractionRepository.save(extraction);

        // Extraire la référence depuis l'OCR
        String accordRef = extractAccordReference(ocrText);
        log.info("🔍 Référence extraite pour l'accord : {}", accordRef);

        // Indexation FAISS avec référence
        addAccordToFaissIndex(ocrText, Paths.get(accordPath).getFileName().toString(), fullPath.toString(), accordRef);

        return ocrText;
    }
    private void addAccordToFaissIndex(String text, String filename, String absolutePath, String accordReference) {
        try {
            String url = faissAddAccordUrl; // utilise le @Value
            Map<String, Object> body = new HashMap<>();
            body.put("text", text);
            body.put("filename", filename);
            body.put("filepath", absolutePath);
            body.put("accord_reference", accordReference);
            restTemplate.postForObject(url, body, Map.class);
            log.info("Accord indexed with reference: {} -> {}", accordReference, filename);
        } catch (Exception e) {
            log.error("Accord FAISS indexing error: {}", e.getMessage());
        }
    }

    private String extractAccordReference(String accordOcrText) {
        if (accordOcrText == null || accordOcrText.isBlank()) return null;
        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile(
                "[Rr][e\u00e9]f(?:\u00e9rence)?[.\\s]*[:\\-]\\s*([A-Z]{1,6}-\\d{2,6}-[A-Z0-9]{2,20})",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher m1 = p1.matcher(accordOcrText);
        if (m1.find()) {
            return m1.group(1).trim();
        }
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("\\b([A-Z]{2,4}-\\d{4}-\\d{2,6})\\b");
        java.util.regex.Matcher m2 = p2.matcher(accordOcrText);
        if (m2.find()) {
            return m2.group(1).trim();
        }
        log.warn("No accord reference found in OCR text");
        return null;
    }
    @Async
    @Transactional
    public void processAfterUpdate(Long noteId, List<Integer> changedLineIndices, boolean accordChanged,
                                   String oldAccordPath, Map<Integer, String> oldLinePaths) {
        log.info("=== START async post-update processing for note {}", noteId);
        ExpenseNote note = noteRepository.findById(noteId).orElse(null);
        if (note == null) return;

        List<ExpenseLine> allLines = lineRepository.findByExpenseNoteId(noteId);

        // 1. Process changed lines
        for (Integer idx : changedLineIndices) {
            if (idx < allLines.size()) {
                ExpenseLine line = allLines.get(idx);
                String oldPath = oldLinePaths.get(idx);
                processLineAfterUpdate(line, note, oldPath);
            }
        }

        // 2. Process accord if changed
        if (accordChanged) {
            processAccordAfterUpdate(note, oldAccordPath);
        }

        log.info("=== END async post-update processing for note {}", noteId);
    }

    private void processLineAfterUpdate(ExpenseLine line, ExpenseNote note, String oldPath) {
        String employeeId = note.getEmployeeId();
        try {
            // 🔥 FORCE FRESH EXTRACTION: delete any existing OCR for this line
            extractionRepository.deleteByExpenseLineId(line.getId());

            // Now re-run extraction – will call /analyze and store new OCR
            String ocrText = ensureLineExtraction(line, employeeId, note.getId());

            // --- Duplicate detection ---
            Path fullPath = Paths.get(uploadsDir, employeeId, line.getJustificatifPath()).toAbsolutePath();
            String filepath = fullPath.toString();
            Map<String, String> payload = Map.of(
                    "ocrText", ocrText,
                    "filepath", filepath,
                    "excludePath", filepath
            );
            Map<String, Object> result = restTemplate.postForObject(
                    checkDuplicateUrl + "?employeeId=" + employeeId,
                    payload,
                    Map.class
            );

            // Clean old duplicate records using the old path (if any) and the new path
            if (oldPath != null) {
                duplicateRepository.deleteByExpenseNoteIdAndExpenseLineIdAndUploadedFile(
                        note.getId(), line.getId(), oldPath
                );
            }
            duplicateRepository.deleteByExpenseNoteIdAndExpenseLineIdAndUploadedFile(
                    note.getId(), line.getId(), line.getJustificatifPath()
            );

            if (result != null) {
                boolean duplicate = (boolean) result.getOrDefault("duplicate", false);
                double score = Double.parseDouble(result.get("score").toString());
                String matchedPath = (String) result.getOrDefault("matchedPath", result.get("path"));

                ExpenseLine managedLine = lineRepository.findById(line.getId()).orElse(line);
                if (duplicate && matchedPath != null) {
                    ExpenseDuplicate dup = new ExpenseDuplicate();
                    dup.setExpenseNoteId(note.getId());
                    dup.setExpenseLineId(line.getId());
                    dup.setUploadedFile(line.getJustificatifPath());
                    dup.setDuplicateFile(matchedPath.length() > 255 ? matchedPath.substring(0,255) : matchedPath);
                    dup.setSimilarity(score);
                    duplicateRepository.save(dup);

                    managedLine.setDuplicateDetected(true);
                    managedLine.setDuplicateScore(score);
                    managedLine.setDuplicateMatchedFile(dup.getDuplicateFile());
                } else {
                    managedLine.setDuplicateDetected(false);
                    managedLine.setDuplicateScore(null);
                    managedLine.setDuplicateMatchedFile(null);
                }
                lineRepository.save(managedLine);
            }

            // --- Anomaly detection (same as creation) ---
            try {
                Map<String, Object> anomalyResult = restTemplate.postForObject(
                        anomalyUrl,
                        Map.of("employeeId", note.getEmployeeId(),
                                "amount", line.getAmount(),
                                "categoryId", line.getCategoryId()),
                        Map.class
                );
                if (anomalyResult != null) {
                    Boolean isAnomaly = (Boolean) anomalyResult.get("anomaly");
                    String message = (String) anomalyResult.get("message");
                    line.setIsAnomalyDepense(isAnomaly != null ? isAnomaly : false);
                    line.setAnomalyExpenseMessage(message);
                    lineRepository.save(line);
                }
            } catch (Exception e) {
                log.warn("Anomaly detection unavailable: {}", e.getMessage());
                line.setIsAnomalyDepense(false);
                line.setAnomalyExpenseMessage("IA indisponible");
                lineRepository.save(line);
            }

            // Re-index in FAISS with the new content
            addToFaissIndex(ocrText, line.getJustificatifPath(), filepath);

        } catch (Exception e) {
            log.error("Error processing line {} after update: {}", line.getId(), e.getMessage(), e);
        }
    }

    private void processAccordAfterUpdate(ExpenseNote note, String oldAccordPath) {
        try {
            String employeeId = note.getEmployeeId();

            // 🔥 FORCE FRESH EXTRACTION: delete any existing accord extraction for this note
            noteExtractionRepository.deleteByExpenseNoteId(note.getId());

            // Now re-run extraction – will call /analyze-for-accord and store new OCR
            String accordOcrText = ensureAccordExtraction(note.getId(), employeeId, note.getAccordPath());

            // --- Duplicate detection for accord ---
            Path accordFullPath = Paths.get(uploadsDir, employeeId, note.getAccordPath()).toAbsolutePath();
            String filepath = accordFullPath.toString();
            Map<String, String> payload = Map.of(
                    "ocrText", accordOcrText,
                    "filepath", filepath,
                    "excludePath", filepath
            );
            Map<String, Object> result = restTemplate.postForObject(
                    checkDuplicateUrl + "?employeeId=" + employeeId,
                    payload,
                    Map.class
            );

            // Remove old accord duplicate records (old path + new path cleanup)
            if (oldAccordPath != null) {
                duplicateRepository.deleteByExpenseNoteIdAndExpenseLineIdIsNullAndUploadedFile(
                        note.getId(), oldAccordPath
                );
            }
            duplicateRepository.deleteByExpenseNoteIdAndExpenseLineIdIsNullAndUploadedFile(
                    note.getId(), note.getAccordPath()
            );

            if (result != null) {
                boolean duplicate = (boolean) result.getOrDefault("duplicate", false);
                double score = Double.parseDouble(result.get("score").toString());
                String matchedPath = (String) result.getOrDefault("matchedPath", result.get("path"));
                if (duplicate && matchedPath != null) {
                    ExpenseDuplicate dup = new ExpenseDuplicate();
                    dup.setExpenseNoteId(note.getId());
                    dup.setExpenseLineId(null);
                    dup.setUploadedFile(note.getAccordPath());
                    dup.setDuplicateFile(matchedPath.length() > 255 ? matchedPath.substring(0,255) : matchedPath);
                    dup.setSimilarity(score);
                    duplicateRepository.save(dup);
                }
            }

            // --- Re-run full AI analysis (accord vs form) ---
            List<ExpenseLine> lines = lineRepository.findByExpenseNoteId(note.getId());
            String analysisResult = expenseService.callFullAnalysis(note, lines, note.getAccordPath());
            if (analysisResult != null) {
                note.setAiAnalysisResult(analysisResult);
                noteRepository.save(note);
            }

            // Re-index accord in FAISS with the new content
            addAccordToFaissIndex(accordOcrText,
                    Paths.get(note.getAccordPath()).getFileName().toString(),
                    filepath,
                    extractAccordReference(accordOcrText));

        } catch (Exception e) {
            log.error("Error processing accord after update for note {}: {}", note.getId(), e.getMessage(), e);
        }
    }
    /**
     * Compare une facture avec les données saisies dans le formulaire pour une ligne
     */
    private Map<String, Object> compareLineWithInvoice(Map<String, Object> invoiceData,
                                                       Map<String, Object> formLineData,
                                                       Integer lineIndex) {
        log.info("📌 compareLineWithInvoice appelé pour ligne {}", lineIndex);

        String url = pythonBaseUrl + "/compare-line-with-invoice";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("invoice_data", invoiceData);
        requestBody.put("form_data", formLineData);
        if (lineIndex != null) {
            requestBody.put("line_index", lineIndex);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(url, requestEntity, Map.class);
            log.info("📥 Résultat comparaison ligne {}: consistent={}", lineIndex, result.get("is_consistent"));
            return result;
        } catch (Exception e) {
            log.error("❌ Erreur comparaison ligne {}: {}", lineIndex, e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("is_consistent", false);
            errorResult.put("confidence", 0.0);
            errorResult.put("issues", List.of("Service de comparaison indisponible"));
            errorResult.put("summary", "Service IA indisponible pour la comparaison");
            errorResult.put("correction_suggestions", new HashMap<>());
            return errorResult;
        }
    }

    /**
     * Construit les données du formulaire à partir d'une ligne
     */
    /**
     * Construit les données du formulaire à partir d'une ligne
     */
    private Map<String, Object> buildFormDataFromLine(ExpenseLine line) {
        Map<String, Object> formData = new HashMap<>();
        formData.put("amount", line.getAmount());
        formData.put("expenseDate", line.getExpenseDate().toString());
        formData.put("description", line.getDescription() != null ? line.getDescription() : "");
        formData.put("categoryId", line.getCategoryId());
        formData.put("categoryName", getCategoryName(line.getCategoryId()));



        // Champs dynamiques existants
        if (line.getDepart() != null) formData.put("depart", line.getDepart());
        if (line.getDestination() != null) formData.put("destination", line.getDestination());
        if (line.getTransportType() != null) formData.put("transportType", line.getTransportType());
        if (line.getHotelName() != null) formData.put("hotelName", line.getHotelName());
        if (line.getNombreNuits() != null) formData.put("nombreNuits", line.getNombreNuits());
        if (line.getKilometrage() != null) formData.put("kilometrage", line.getKilometrage());
        if (line.getNombrePersonnes() != null) formData.put("nombrePersonnes", line.getNombrePersonnes());
        if (line.getRepasType() != null) formData.put("repasType", line.getRepasType());

        return formData;
    }

    /**
     * Récupère le nom de la catégorie par ID
     */

    private String getCategoryName(Long categoryId) {
        try {
            // Utiliser categoryService directement
            return categoryService.getCategoryName(categoryId);
        } catch (Exception e) {
            log.warn("Impossible de récupérer le nom de la catégorie {}: {}", categoryId, e.getMessage());
            return "Catégorie " + categoryId;
        }
    }

    /**
     * Sauvegarde le résultat de comparaison dans la ligne
     */
    private void saveComparisonResult(ExpenseLine line, Map<String, Object> comparison) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            line.setInvoiceFormComparison(mapper.writeValueAsString(comparison));
            line.setIsConsistentWithInvoice((Boolean) comparison.getOrDefault("is_consistent", false));
            line.setConsistencyConfidence((Double) comparison.getOrDefault("confidence", 0.0));

            @SuppressWarnings("unchecked")
            List<String> issues = (List<String>) comparison.getOrDefault("issues", List.of());
            line.setConsistencyIssues(String.join("; ", issues));

            Map<String, Object> suggestions = (Map<String, Object>) comparison.get("correction_suggestions");
            if (suggestions != null) {
                if (suggestions.get("suggested_amount") != null) {
                    line.setSuggestedAmount(Double.valueOf(suggestions.get("suggested_amount").toString()));
                }
                if (suggestions.get("suggested_date") != null) {
                    line.setSuggestedDate(LocalDate.parse(suggestions.get("suggested_date").toString()));
                }
                if (suggestions.get("suggested_description") != null) {
                    line.setSuggestedDescription(suggestions.get("suggested_description").toString());
                }
                if (suggestions.get("suggested_category_id") != null) {
                    line.setSuggestedCategoryId(Long.valueOf(suggestions.get("suggested_category_id").toString()));
                }
            }

            lineRepository.save(line);
            log.info("✅ Résultat comparaison sauvegardé pour ligne {}", line.getId());
        } catch (Exception e) {
            log.error("❌ Erreur sauvegarde comparaison ligne {}: {}", line.getId(), e.getMessage());
        }
    }
    /**
     * Copie EXACTE de processAfterSubmission mais avec les devises
     */
    @Async
    @Transactional
    public void processAfterSubmissionWithCurrency(Long noteId, List<ExpenseLine> lines,
                                                   Map<Long, Map<String, Object>> currencyInfos) {
        log.info("=== START async processing WITH CURRENCY for note {}", noteId);
        ExpenseNote note = noteRepository.findById(noteId).orElse(null);
        if (note == null) {
            log.error("Note {} not found", noteId);
            return;
        }
        String employeeId = note.getEmployeeId();

        // 1. Process each expense line (justificatif)
        for (ExpenseLine line : lines) {
            try {
                // ✅ Récupérer les infos de devise
                Map<String, Object> currencyInfo = currencyInfos.get(line.getId());
                String invoiceCurrency = currencyInfo != null ? (String) currencyInfo.get("invoiceCurrency") : "TND";
                Double rateToTND = currencyInfo != null ? (Double) currencyInfo.get("rateToTND") : 1.0;

                log.info("📄 Ligne {}: invoiceCurrency={}, rateToTND={}", line.getId(), invoiceCurrency, rateToTND);

                // Ensure OCR is available (calls /analyze if missing) - AVEC DEVISES
                String ocrText = ensureLineExtractionWithCurrency(line, employeeId, noteId, invoiceCurrency, rateToTND);

                // --- INDEXATION SYSTÉMATIQUE ---
                Path fullPath = Paths.get(uploadsDir, employeeId, line.getJustificatifPath()).toAbsolutePath();
                String absolutePath = fullPath.toString();
                addToFaissIndex(ocrText, line.getJustificatifPath(), absolutePath);
                // -----------------------------

                // Validation (via JSON)
                Optional<ExpenseExtraction> optExt = extractionRepository.findByExpenseLineId(line.getId());
                if (optExt.isPresent() && optExt.get().getExtractedJson() != null) {
                    Map<String, Object> extractedJson = optExt.get().getExtractedJson();
                    try {
                        Map<String, Object> validationResult = restTemplate.postForObject(
                                validateFromJsonUrl,
                                extractedJson,
                                Map.class
                        );
                        if (validationResult != null) {
                            Boolean isValid = (Boolean) validationResult.getOrDefault("is_accurate", false);
                            Double confidence = (Double) validationResult.getOrDefault("confidence", 0.0);
                            @SuppressWarnings("unchecked")
                            List<String> issues = (List<String>) validationResult.getOrDefault("issues", List.of());
                            String issuesStr = String.join("; ", issues);
                            line.setValidationValid(isValid);
                            line.setValidationScore(confidence);
                            line.setValidationIssues(issuesStr);
                            lineRepository.save(line);
                            log.info("✅ Validation saved for line {}", line.getId());
                        }
                    } catch (Exception e) {
                        log.error("Validation error line {}: {}", line.getId(), e.getMessage());
                    }
                }

                // Duplicate detection
                String filepath = fullPath.toString();
                String excludePath = filepath;
                Map<String, String> payload = Map.of(
                        "ocrText", ocrText,
                        "filepath", filepath,
                        "excludePath", excludePath
                );
                Map<String, Object> result = restTemplate.postForObject(
                        checkDuplicateUrl + "?employeeId=" + employeeId,
                        payload,
                        Map.class
                );
                if (result != null) {
                    boolean duplicate = (boolean) result.getOrDefault("duplicate", false);
                    double score = Double.parseDouble(result.get("score").toString());
                    String matchedPath = (String) result.get("matchedPath");
                    if (matchedPath == null) matchedPath = (String) result.get("path");

                    ExpenseLine managedLine = lineRepository.findById(line.getId()).orElse(null);
                    if (managedLine == null) {
                        log.error("Line {} not found", line.getId());
                        continue;
                    }

                    if (duplicate && matchedPath != null) {
                        boolean alreadyExists = duplicateRepository.existsByExpenseNoteIdAndExpenseLineIdAndUploadedFile(
                                noteId, line.getId(), line.getJustificatifPath());
                        if (!alreadyExists) {
                            String finalPath = matchedPath.length() > 255 ? matchedPath.substring(0, 255) : matchedPath;
                            ExpenseDuplicate dup = new ExpenseDuplicate();
                            dup.setExpenseNoteId(noteId);
                            dup.setExpenseLineId(line.getId());
                            dup.setUploadedFile(line.getJustificatifPath());
                            dup.setDuplicateFile(finalPath);
                            dup.setSimilarity(score);
                            duplicateRepository.save(dup);
                            log.info("✅ Duplicate saved for line {}", line.getId());
                        }
                        managedLine.setDuplicateDetected(true);
                        managedLine.setDuplicateScore(score);
                        if (matchedPath != null) {
                            String finalPath = matchedPath.length() > 255 ? matchedPath.substring(0, 255) : matchedPath;
                            managedLine.setDuplicateMatchedFile(finalPath);
                        }
                        lineRepository.save(managedLine);
                    } else {
                        managedLine.setDuplicateDetected(false);
                        lineRepository.save(managedLine);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing line {}: {}", line.getId(), e.getMessage(), e);
            }
        }

        // 2. Process accord (if present) - IDENTIQUE
        if (note.getAccordPath() != null && !note.getAccordPath().isEmpty()) {
            try {
                String accordOcrText = ensureAccordExtraction(noteId, employeeId, note.getAccordPath());

                Path accordFullPath = Paths.get(uploadsDir, employeeId, note.getAccordPath()).toAbsolutePath();
                String absolutePath = accordFullPath.toString();
                String accordFileName = Paths.get(note.getAccordPath()).getFileName().toString();
                addToFaissIndex(accordOcrText, accordFileName, absolutePath);

                String filepath = absolutePath;
                String excludePath = filepath;
                Map<String, String> payload = Map.of(
                        "ocrText", accordOcrText,
                        "filepath", filepath,
                        "excludePath", excludePath
                );
                Map<String, Object> result = restTemplate.postForObject(
                        checkDuplicateUrl + "?employeeId=" + employeeId,
                        payload,
                        Map.class
                );
                if (result != null) {
                    boolean duplicate = (boolean) result.getOrDefault("duplicate", false);
                    double score = Double.parseDouble(result.get("score").toString());
                    String matchedPath = (String) result.get("matchedPath");
                    if (matchedPath == null) matchedPath = (String) result.get("path");

                    if (duplicate && matchedPath != null) {
                        boolean alreadyExists = duplicateRepository.existsByExpenseNoteIdAndExpenseLineIdIsNullAndUploadedFile(
                                noteId, note.getAccordPath());
                        if (!alreadyExists) {
                            String finalPath = matchedPath.length() > 255 ? matchedPath.substring(0, 255) : matchedPath;
                            ExpenseDuplicate dup = new ExpenseDuplicate();
                            dup.setExpenseNoteId(noteId);
                            dup.setExpenseLineId(null);
                            dup.setUploadedFile(note.getAccordPath());
                            dup.setDuplicateFile(finalPath);
                            dup.setSimilarity(score);
                            duplicateRepository.save(dup);
                            log.info("✅ Accord duplicate saved for note {}", noteId);
                        }
                    }
                }

                String analysisResult = expenseService.callFullAnalysis(note, lines, note.getAccordPath());
                if (analysisResult != null) {
                    note.setAiAnalysisResult(analysisResult);
                    noteRepository.save(note);
                    log.info("✅ Full AI analysis saved for note {}", noteId);
                }

            } catch (Exception e) {
                log.error("Error processing accord for note {}: {}", noteId, e.getMessage(), e);
            }
        }

        log.info("=== END async processing WITH CURRENCY for note {}", noteId);
    }

    /**
     * Version de ensureLineExtraction avec devises
     */
    private String ensureLineExtractionWithCurrency(ExpenseLine line, String employeeId, Long expenseNoteId,
                                                    String invoiceCurrency, Double rateToTND) throws Exception {
        Optional<ExpenseExtraction> existing = extractionRepository.findByExpenseLineId(line.getId());
        Map<String, Object> structured = null;

        if (existing.isPresent() && existing.get().getOcrText() != null) {
            structured = existing.get().getExtractedJson();
            log.info("📄 Extraction existante trouvée pour ligne {}", line.getId());
        } else {
            Path fullPath = Paths.get(uploadsDir, employeeId, line.getJustificatifPath()).toAbsolutePath();
            if (!Files.exists(fullPath)) {
                throw new RuntimeException("File not found: " + fullPath);
            }
            byte[] fileBytes = Files.readAllBytes(fullPath);

            String analyzeUrl = faissAddUrl.replace("/add-to-index", "/analyze");
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(fileBytes) {
                @Override public String getFilename() { return fullPath.getFileName().toString(); }
            });
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            Map<String, Object> response = restTemplate.postForObject(analyzeUrl, request, Map.class);
            String ocrText = (String) response.get("ocr_text");
            structured = (Map<String, Object>) response.get("structured");

            ExpenseExtraction extraction = ExpenseExtraction.builder()
                    .expenseLineId(line.getId())
                    .ocrText(ocrText)
                    .extractedJson(structured)
                    .extractionVersion("v1")
                    .build();
            extractionRepository.save(extraction);
            log.info("✅ Nouvelle extraction sauvegardée pour ligne {}", line.getId());
        }

        // ========== ✅ CORRECTION ICI ==========
        if (structured != null && !structured.isEmpty()) {
            try {
                // ✅ Utiliser la version AVEC devises !
                Map<String, Object> formData = buildFormDataFromLineWithCurrency(line, invoiceCurrency, rateToTND);
                log.info("🔍 Comparaison ligne {} - invoiceCurrency={}, rateToTND={}",
                        line.getId(), invoiceCurrency, rateToTND);

                Map<String, Object> comparisonResult = compareLineWithInvoice(structured, formData, 0);
                saveComparisonResult(line, comparisonResult);

                Boolean isConsistent = (Boolean) comparisonResult.getOrDefault("is_consistent", false);
                if (!isConsistent) {
                    log.warn("⚠️ Incohérence détectée pour ligne {}", line.getId());
                } else {
                    log.info("✅ Ligne {} cohérente avec la facture", line.getId());
                }
            } catch (Exception e) {
                log.error("❌ Erreur comparaison ligne {}: {}", line.getId(), e.getMessage(), e);
            }
        }

        String ocrText = existing.isPresent() && existing.get().getOcrText() != null ?
                existing.get().getOcrText() : "Extracted";

        return ocrText;
    }

    /**
     * Version de buildFormDataFromLine avec devises
     */
    private Map<String, Object> buildFormDataFromLineWithCurrency(ExpenseLine line,
                                                                  String invoiceCurrency,
                                                                  Double rateToTND) {
        Map<String, Object> formData = new HashMap<>();
        formData.put("amount", line.getAmount());
        formData.put("expenseDate", line.getExpenseDate().toString());
        formData.put("description", line.getDescription() != null ? line.getDescription() : "");
        formData.put("categoryId", line.getCategoryId());
        formData.put("categoryName", getCategoryName(line.getCategoryId()));

        // ✅ Ajouter les devises
        formData.put("invoiceCurrency", invoiceCurrency != null ? invoiceCurrency : "TND");
        formData.put("rateToTND", rateToTND != null ? rateToTND : 1.0);

        // Champs dynamiques
        if (line.getDepart() != null) formData.put("depart", line.getDepart());
        if (line.getDestination() != null) formData.put("destination", line.getDestination());
        if (line.getTransportType() != null) formData.put("transportType", line.getTransportType());
        if (line.getHotelName() != null) formData.put("hotelName", line.getHotelName());
        if (line.getNombreNuits() != null) formData.put("nombreNuits", line.getNombreNuits());
        if (line.getKilometrage() != null) formData.put("kilometrage", line.getKilometrage());
        if (line.getNombrePersonnes() != null) formData.put("nombrePersonnes", line.getNombrePersonnes());
        if (line.getRepasType() != null) formData.put("repasType", line.getRepasType());

        return formData;
    }

}