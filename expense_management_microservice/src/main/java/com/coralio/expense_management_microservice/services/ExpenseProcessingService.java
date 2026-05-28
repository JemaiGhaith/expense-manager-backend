package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.entities.*;
import com.coralio.expense_management_microservice.repos.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    // ✅ ADDED: inject ExpenseService to call full analysis
    @Autowired
    private ExpenseService expenseService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadsDir;
    @Value("${ai.faiss.add.url:http://localhost:9000/add-to-index}")
    private String faissAddUrl;

    @Value("${ai.validate.from.json.url:http://localhost:8085/api/ai/validate-from-json}")
    private String validateFromJsonUrl;

    @Value("${ai.check.duplicate.url:http://localhost:8085/api/ai/check-duplicate-from-text}")
    private String checkDuplicateUrl;

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
        if (existing.isPresent() && existing.get().getOcrText() != null) {
            return existing.get().getOcrText();
        }

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
        Map<String, Object> structured = (Map<String, Object>) response.get("structured");

        ExpenseExtraction extraction = ExpenseExtraction.builder()
                .expenseLineId(line.getId())
                .ocrText(ocrText)
                .extractedJson(structured)
                .extractionVersion("v1")
                .build();
        extractionRepository.save(extraction);
        log.info("✅ Fallback extraction saved for line {}", line.getId());

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
        Map<String, Object> structured = (Map<String, Object>) response.get("structured");

        ExpenseNoteExtraction extraction = ExpenseNoteExtraction.builder()
                .expenseNoteId(expenseNoteId)
                .ocrText(ocrText)
                .extractedJson(structured)
                .extractionVersion("v1")
                .build();
        noteExtractionRepository.save(extraction);
        log.info("✅ Fallback accord extraction saved for note {}", expenseNoteId);

        return ocrText;
    }
}