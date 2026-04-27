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

    @Value("${file.upload-dir:./uploads}")
    private String uploadsDir;

    // Helper to resolve temporary paths (kept as is)
    private String resolveRealDuplicatePath(String tempPath, Long currentNoteId) {
        if (tempPath == null) {
            log.warn("⚠️ resolveRealDuplicatePath called with null tempPath");
            return null;
        }
        if (!tempPath.contains("/tmp/") && !tempPath.contains("\\tmp\\")) {
            return tempPath;
        }
        String fileName = Paths.get(tempPath).getFileName().toString();
        List<ExpenseLine> lines = lineRepository.findByJustificatifPathEndingWith(fileName);
        for (ExpenseLine line : lines) {
            Long otherNoteId = line.getExpenseNoteId();
            if (otherNoteId.equals(currentNoteId)) continue;
            ExpenseNote otherNote = noteRepository.findById(otherNoteId).orElse(null);
            if (otherNote == null) continue;
            String employeeId = otherNote.getEmployeeId();
            Path fullPath = Paths.get(uploadsDir, employeeId, line.getJustificatifPath()).toAbsolutePath();
            if (Files.exists(fullPath)) {
                log.info("✅ Real duplicate path found: {}", fullPath);
                return fullPath.toString();
            }
        }
        log.warn("⚠️ No real path found, using temporary: {}", tempPath);
        return tempPath;
    }

    // Helper to add a document to FAISS (reuses the same logic as ExpenseService)
    private void addToFaissIndex(String text, String filename, String absolutePath) {
        try {
            String url = "http://localhost:9000/add-to-index";
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

        // ─────────────────────────────────────────────────────────
        // 1. Process each expense line (justificatif)
        // ─────────────────────────────────────────────────────────
        for (ExpenseLine line : lines) {
            try {
                // 1.1 Ensure OCR is available (calls /analyze if missing)
                String ocrText = ensureLineExtraction(line, employeeId, noteId);

                // 1.2 Get the extraction (now guaranteed to exist)
                Optional<ExpenseExtraction> optExt = extractionRepository.findByExpenseLineId(line.getId());
                if (optExt.isPresent() && optExt.get().getExtractedJson() != null) {
                    Map<String, Object> extractedJson = optExt.get().getExtractedJson();

                    // 1.3 Call validation
                    Map<String, Object> validationResult = null;
                    try {
                        validationResult = restTemplate.postForObject(
                                "http://localhost:8085/api/ai/validate-from-json",
                                extractedJson,
                                Map.class
                        );
                        log.info("📥 Validation response for line {}: {}", line.getId(), validationResult);
                    } catch (Exception e) {
                        log.error("Validation error line {}: {}", line.getId(), e.getMessage());
                    }

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
                }

                // 1.4 Duplicate detection
                Path fullPath = Paths.get(uploadsDir, employeeId, line.getJustificatifPath()).toAbsolutePath();
                String filepath = fullPath.toString();
                String excludePath = filepath;
                Map<String, String> payload = Map.of(
                        "ocrText", ocrText,
                        "filepath", filepath,
                        "excludePath", excludePath
                );
                Map<String, Object> result = restTemplate.postForObject(
                        "http://localhost:8085/api/ai/check-duplicate-from-text?employeeId=" + employeeId,
                        payload,
                        Map.class
                );
                if (result != null) {
                    boolean duplicate = (boolean) result.getOrDefault("duplicate", false);
                    double score = Double.parseDouble(result.get("score").toString());
                    String matchedPath = (String) result.get("matchedPath");
                    if (matchedPath == null) matchedPath = (String) result.get("path");

                    if (duplicate && matchedPath != null) {
                        String finalPath = matchedPath.length() > 255 ? matchedPath.substring(0, 255) : matchedPath;
                        ExpenseDuplicate dup = new ExpenseDuplicate();
                        dup.setExpenseNoteId(noteId);
                        dup.setExpenseLineId(line.getId());
                        dup.setUploadedFile(line.getJustificatifPath());
                        dup.setDuplicateFile(finalPath);
                        dup.setSimilarity(score);
                        duplicateRepository.save(dup);
                        log.info("✅ Duplicate saved for line {}", line.getId());

                        line.setDuplicateDetected(true);
                        line.setDuplicateScore(score);
                        line.setDuplicateMatchedFile(finalPath);
                        lineRepository.save(line);
                    } else {
                        line.setDuplicateDetected(false);
                        lineRepository.save(line);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing line {}: {}", line.getId(), e.getMessage(), e);
            }
        }

        // ─────────────────────────────────────────────────────────
        // 2. Process accord (if present)
        // ─────────────────────────────────────────────────────────
        if (note.getAccordPath() != null && !note.getAccordPath().isEmpty()) {
            try {
                String accordOcrText = ensureAccordExtraction(noteId, employeeId, note.getAccordPath());

                // Duplicate detection for accord
                Path accordFullPath = Paths.get(uploadsDir, employeeId, note.getAccordPath()).toAbsolutePath();
                String filepath = accordFullPath.toString();
                String excludePath = filepath;
                Map<String, String> payload = Map.of(
                        "ocrText", accordOcrText,
                        "filepath", filepath,
                        "excludePath", excludePath
                );
                Map<String, Object> result = restTemplate.postForObject(
                        "http://localhost:8085/api/ai/check-duplicate-from-text?employeeId=" + employeeId,
                        payload,
                        Map.class
                );
                if (result != null) {
                    boolean duplicate = (boolean) result.getOrDefault("duplicate", false);
                    double score = Double.parseDouble(result.get("score").toString());
                    String matchedPath = (String) result.get("matchedPath");
                    if (matchedPath == null) matchedPath = (String) result.get("path");

                    if (duplicate && matchedPath != null) {
                        String finalPath = matchedPath.length() > 255 ? matchedPath.substring(0, 255) : matchedPath;
                        ExpenseDuplicate dup = new ExpenseDuplicate();
                        dup.setExpenseNoteId(noteId);
                        dup.setExpenseLineId(null);   // signifies accord
                        dup.setUploadedFile(note.getAccordPath());
                        dup.setDuplicateFile(finalPath);
                        dup.setSimilarity(score);
                        duplicateRepository.save(dup);
                        log.info("✅ Accord duplicate saved for note {}", noteId);
                    } else {
                        log.info("No duplicate found for accord of note {}", noteId);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing accord for note {}: {}", noteId, e.getMessage(), e);
            }
        }

        log.info("=== END async processing for note {}", noteId);
    }

    // ─────────────────────────────────────────────────────────
    // Helper methods for extraction with fallback
    // ─────────────────────────────────────────────────────────

    private String ensureLineExtraction(ExpenseLine line, String employeeId, Long expenseNoteId) throws Exception {
        // 1. Check if extraction already exists in DB
        Optional<ExpenseExtraction> existing = extractionRepository.findByExpenseLineId(line.getId());
        if (existing.isPresent() && existing.get().getOcrText() != null) {
            return existing.get().getOcrText();
        }

        // 2. Fallback: read file from disk and call /analyze
        Path fullPath = Paths.get(uploadsDir, employeeId, line.getJustificatifPath()).toAbsolutePath();
        if (!Files.exists(fullPath)) {
            throw new RuntimeException("File not found: " + fullPath);
        }
        byte[] fileBytes = Files.readAllBytes(fullPath);

        String analyzeUrl = "http://localhost:9000/analyze";
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

        // Save to expense_extractions
        ExpenseExtraction extraction = ExpenseExtraction.builder()
                .expenseLineId(line.getId())
                .ocrText(ocrText)
                .extractedJson(structured)
                .extractionVersion("v1")
                .build();
        extractionRepository.save(extraction);
        log.info("✅ Fallback extraction saved for line {}", line.getId());

        // Also add to FAISS with absolute path (so future duplicates are detected)
        String absolutePath = fullPath.toString();
        String textToIndex = line.getDescription() != null ? line.getDescription() : "facture";
        addToFaissIndex(textToIndex, line.getJustificatifPath(), absolutePath);

        return ocrText;
    }

    private String ensureAccordExtraction(Long expenseNoteId, String employeeId, String accordPath) throws Exception {
        // 1. Check if extraction already exists
        Optional<ExpenseNoteExtraction> existing = noteExtractionRepository.findByExpenseNoteId(expenseNoteId);
        if (existing.isPresent() && existing.get().getOcrText() != null) {
            return existing.get().getOcrText();
        }

        // 2. Fallback: read accord file and call /analyze
        Path fullPath = Paths.get(uploadsDir, employeeId, accordPath).toAbsolutePath();
        if (!Files.exists(fullPath)) {
            throw new RuntimeException("Accord file not found: " + fullPath);
        }
        byte[] fileBytes = Files.readAllBytes(fullPath);

        String analyzeUrl = "http://localhost:9000/analyze";
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

        // Save to note extraction table
        ExpenseNoteExtraction extraction = ExpenseNoteExtraction.builder()
                .expenseNoteId(expenseNoteId)
                .ocrText(ocrText)
                .extractedJson(structured)
                .extractionVersion("v1")
                .build();
        noteExtractionRepository.save(extraction);
        log.info("✅ Fallback accord extraction saved for note {}", expenseNoteId);

        // Also add to FAISS with absolute path (so accord is indexed even if sync extraction failed)
        String absolutePath = fullPath.toString();
        String filename = Paths.get(accordPath).getFileName().toString();
        addToFaissIndex(ocrText, filename, absolutePath);

        return ocrText;
    }
}