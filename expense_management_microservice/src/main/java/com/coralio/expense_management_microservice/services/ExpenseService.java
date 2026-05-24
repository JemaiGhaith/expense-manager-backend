package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.client.UserServiceClient;
import com.coralio.expense_management_microservice.client.NotificationClient;
import com.coralio.expense_management_microservice.entities.*;
import com.coralio.expense_management_microservice.repos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Function;

@Slf4j
@Service
public class ExpenseService {

    // ========== DEPENDENCIES ==========
    private final CategoryService categoryService;
    private final UserServiceClient userServiceClient;
    private final NotificationClient notificationClient;
    private final ProjectRepository projectRepository;
    private final ExpenseNoteRepository noteRepository;
    private final ExpenseLineRepository lineRepository;
    private final DatabaseMigrationService migrationService;
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final Set<String> TUNISIA_HOLIDAYS = Set.of(
            "01-01", "14-01", "20-03", "09-04", "01-05",
            "25-07", "13-08", "15-10", "17-12"
    );
    private final Map<String, String> columnTypeCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    private OCRService ocrService;
    @Autowired
    private FileStorageService fileStorageService;

    private final ExpenseExtractionRepository extractionRepository;
    private final ExpenseNoteExtractionRepository noteExtractionRepository;
    private final CurrencyService currencyService;
    private final ExpenseNoteInternalHistoryRepository internalHistoryRepository;
    private final ReimbursementService reimbursementService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadsDir;
    @Value("${ai.anomaly.url:http://localhost:9000/detect-anomaly-ai}")
    private String anomalyUrl;

    @Value("${ai.accord.analyze.url:http://localhost:9000/analyze-for-accord}")
    private String accordAnalyzeUrl;

    @Value("${ai.faiss.add.accord.url:http://localhost:9000/add-accord-to-index}")
    private String faissAddAccordUrl;

    @Value("${ai.full.analysis.url:http://localhost:8000/analyze-full}")
    private String fullAnalysisUrl;

    @Value("${project.service.url:http://localhost:8082}")
    private String projectServiceUrl;

    @Value("${user.service.url:http://localhost:8083}")
    private String userServiceUrl;

    @Value("${gateway.url:http://localhost:8888}")
    private String gatewayUrl;

    @Value("${ai.faiss.add.url:http://localhost:9000/add-to-index}")
    private String faissAddUrl;
    // Constructor (merged)
    public ExpenseService(
            ExpenseNoteRepository noteRepository,
            ExpenseLineRepository lineRepository,
            DatabaseMigrationService migrationService,
            ProjectRepository projectRepository,
            JdbcTemplate jdbcTemplate,
            NotificationClient notificationClient,
            UserServiceClient userServiceClient,
            CategoryService categoryService,
            ExpenseExtractionRepository extractionRepository,
            ExpenseNoteExtractionRepository noteExtractionRepository,
            CurrencyService currencyService,
            ExpenseNoteInternalHistoryRepository internalHistoryRepository,
            ReimbursementService reimbursementService) {
        this.noteRepository = noteRepository;
        this.lineRepository = lineRepository;
        this.migrationService = migrationService;
        this.jdbcTemplate = jdbcTemplate;
        this.projectRepository = projectRepository;
        this.notificationClient = notificationClient;
        this.userServiceClient = userServiceClient;
        this.categoryService = categoryService;
        this.extractionRepository = extractionRepository;
        this.noteExtractionRepository = noteExtractionRepository;
        this.currencyService = currencyService;
        this.internalHistoryRepository = internalHistoryRepository;
        this.reimbursementService = reimbursementService;
    }

    // ========== CREATE METHODS (merged: currency + FAISS indexing) ==========
    @Transactional
    public ExpenseNote createExpenseNote(ExpenseNote note, List<ExpenseLine> lines) {
        return createExpenseNoteWithFiles(note, lines, null, null);
    }

    @Transactional
    public ExpenseNote createExpenseNoteWithFiles(
            ExpenseNote note,
            List<ExpenseLine> lines,
            String accordFileName,
            List<String> factureFileNames,
            MultipartFile accordFile,
            String displayCurrency,
            Double exchangeRate) {
        // 1. Préparation de la note
        if (note.getStatus() == null) {
            note.setStatus(ExpenseStatus.EN_ATTENTE);
        }
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        if (accordFileName != null) {
            note.setAccordPath(accordFileName);
        }

        // 2. Validation des dates des lignes
        for (ExpenseLine line : lines) {
            if (line.getExpenseDate() == null) {
                line.setExpenseDate(LocalDate.now());
            }
            validateExpenseDate(line.getExpenseDate());
        }

        // 3. Sauvegarde de la note
        ExpenseNote savedNote = noteRepository.save(note);

        // 4. Traitement de chaque ligne
        for (int i = 0; i < lines.size(); i++) {
            ExpenseLine line = lines.get(i);
            line.setExpenseNoteId(savedNote.getId());

            if (factureFileNames != null && i < factureFileNames.size()) {
                line.setJustificatifPath(factureFileNames.get(i));
            }

            // Détection d'anomalie IA
            try {
                Map<String, Object> result = restTemplate.postForObject(
                        anomalyUrl,
                        Map.of(
                                "employeeId", note.getEmployeeId(),
                                "amount", line.getAmount(),
                                "categoryId", line.getCategoryId()
                        ),
                        Map.class
                );
                Boolean isAnomaly = (Boolean) result.get("anomaly");
                String message = (String) result.get("message");
                line.setIsAnomalyDepense(isAnomaly != null ? isAnomaly : false);
                line.setAnomalyExpenseMessage(message);
                if (Boolean.TRUE.equals(isAnomaly)) {
                    System.out.println("🚨 ANOMALIE DETECTEE ! " + message);
                }
            } catch (Exception e) {
                line.setIsAnomalyDepense(false);
                line.setAnomalyExpenseMessage("IA indisponible");
                System.err.println("❌ Erreur appel IA: " + e.getMessage());
            }

            insertExpenseLineWithDynamicColumns(line);
            checkCategoryLimit(line, savedNote.getEmployeeId(), savedNote.getId(), savedNote.getProjectId());
        }

        // Sauvegarde des extractions OCR pour les lignes
        List<ExpenseLine> savedLines = lineRepository.findByExpenseNoteId(savedNote.getId());
        for (int i = 0; i < savedLines.size() && i < lines.size(); i++) {
            ExpenseLine savedLine = savedLines.get(i);
            ExpenseLine originalLine = lines.get(i);
            if (originalLine.getOcrText() != null || originalLine.getExtractedJson() != null) {
                ExpenseExtraction extraction = ExpenseExtraction.builder()
                        .expenseLineId(savedLine.getId())
                        .ocrText(originalLine.getOcrText())
                        .extractedJson(originalLine.getExtractedJson())
                        .extractionVersion("v1")
                        .build();
                extractionRepository.save(extraction);
                log.debug("✅ Extraction sauvegardée pour lineId: {}", savedLine.getId());
            }
        }

        // ========== ACCORD EXTRACTION + FAISS INDEXING (using /analyze-for-accord) ==========
        if (accordFile != null && !accordFile.isEmpty()) {
            try {
                String accordOcrText = ocrService.extractText(accordFile);
                String analyzeUrl = accordAnalyzeUrl;
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", new ByteArrayResource(accordFile.getBytes()) {
                    @Override public String getFilename() { return accordFile.getOriginalFilename(); }
                });
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
                Map<String, Object> analyzeResponse = restTemplate.postForObject(analyzeUrl, request, Map.class);
                Map<String, Object> structuredJson = (Map<String, Object>) analyzeResponse.get("structured");

                // Sauvegarde en base
                ExpenseNoteExtraction extraction = ExpenseNoteExtraction.builder()
                        .expenseNoteId(savedNote.getId())
                        .ocrText(accordOcrText)
                        .extractedJson(structuredJson)
                        .extractionVersion("v1")
                        .build();
                noteExtractionRepository.save(extraction);
                log.info("✅ Accord extraction saved for note {}", savedNote.getId());

                String pythonOcrText = analyzeResponse.get("ocr_text") instanceof String
                        ? (String) analyzeResponse.get("ocr_text")
                        : accordOcrText;

                String accordRef = extractAccordReference(pythonOcrText);
                log.info("🔍 Référence extraite pour l'accord : {}", accordRef);

                Path accordAbsPath = Paths.get(uploadsDir, savedNote.getEmployeeId(), savedNote.getAccordPath()).toAbsolutePath();
                addAccordToFaissIndex(pythonOcrText, savedNote.getAccordPath(), accordAbsPath.toString(), accordRef);
            } catch (Exception e) {
                log.error("Failed to extract/index accord data for note {}: {}", savedNote.getId(), e.getMessage(), e);
            }
        }

        // 5. Total amount
        double total = lines.stream().mapToDouble(ExpenseLine::getAmount).sum();
        savedNote.setTotalAmount(total);
        savedNote.setUpdatedAt(LocalDateTime.now());

        // ========== NOTIFICATIONS WITH CURRENCY ==========
        String employeeTargetCurrency = displayCurrency;
        Double employeeRate = exchangeRate;
        if (employeeTargetCurrency == null || employeeTargetCurrency.isEmpty()) {
            employeeTargetCurrency = "TND";
            employeeRate = 1.0;
        }
        if (employeeRate == null) {
            try {
                employeeRate = currencyService.getExchangeRate(employeeTargetCurrency);
            } catch (Exception e) {
                log.warn("Could not fetch exchange rate for {}, using 1.0", employeeTargetCurrency);
                employeeRate = 1.0;
            }
        }
        double convertedEmployeeAmount = savedNote.getTotalAmount() * employeeRate;

        try {
            String employeeEmail = getEmployeeEmail(savedNote.getEmployeeId());
            notificationClient.notifyExpenseCreated(
                    UUID.fromString(savedNote.getEmployeeId()),
                    employeeEmail,
                    "EXP-" + savedNote.getId(),
                    savedNote.getTotalAmount(),
                    convertedEmployeeAmount,
                    employeeTargetCurrency,
                    savedNote.getId()
            );
            System.out.println("✅ Creation notification sent to employee: " + savedNote.getEmployeeId());
        } catch (Exception e) {
            System.err.println("❌ Failed to send creation notification: " + e.getMessage());
        }

        // Notification to manager with his/her preferred currency (or the passed one)
        String managerTargetCurrency = displayCurrency;
        Double managerRate = exchangeRate;
        try {
            String managerId = getManagerIdForProjectDepartment(savedNote.getProjectId());
            if (managerId != null) {
                String managerEmail = getEmployeeEmail(managerId);
                String employeeName = getEmployeeName(savedNote.getEmployeeId());

                if (managerTargetCurrency == null || managerTargetCurrency.isEmpty()) {
                    managerTargetCurrency = userServiceClient.getUserPreferredCurrency(managerId);
                    if (managerTargetCurrency == null || managerTargetCurrency.isEmpty()) {
                        managerTargetCurrency = "TND";
                    }
                    try {
                        managerRate = currencyService.getExchangeRate(managerTargetCurrency);
                    } catch (Exception e) {
                        log.warn("Could not fetch exchange rate for {}, using 1.0", managerTargetCurrency);
                        managerRate = 1.0;
                    }
                } else if (managerRate == null) {
                    try {
                        managerRate = currencyService.getExchangeRate(managerTargetCurrency);
                    } catch (Exception e) {
                        log.warn("Could not fetch exchange rate for {}, using 1.0", managerTargetCurrency);
                        managerRate = 1.0;
                    }
                }

                double convertedAmount = savedNote.getTotalAmount() * managerRate;

                notificationClient.notifyManagerPendingApproval(
                        UUID.fromString(managerId), managerEmail, employeeName,
                        "EXP-" + savedNote.getId(),
                        savedNote.getTotalAmount(), convertedAmount, managerTargetCurrency,
                        savedNote.getId()
                );
                System.out.println("✅ Notification envoyée au manager du projet: " + managerId);
            } else {
                System.out.println("⚠️ Aucun manager trouvé pour le projet " + savedNote.getProjectId());
            }
        } catch (Exception e) {
            System.err.println("❌ Erreur envoi notification manager: " + e.getMessage());
        }

        if (managerRate == null) managerRate = 1.0;
        checkProjectBudget(savedNote, savedNote.getId(), managerTargetCurrency, managerRate);

        return noteRepository.save(savedNote);
    }

    // Legacy overloads (backward compatibility)
    @Transactional
    public ExpenseNote createExpenseNoteWithFiles(
            ExpenseNote note,
            List<ExpenseLine> lines,
            String accordFileName,
            List<String> factureFileNames,
            MultipartFile accordFile) {
        return createExpenseNoteWithFiles(note, lines, accordFileName, factureFileNames, accordFile, null, null);
    }

    @Transactional
    public ExpenseNote createExpenseNoteWithFiles(
            ExpenseNote note,
            List<ExpenseLine> lines,
            String accordFileName,
            List<String> factureFileNames) {
        return createExpenseNoteWithFiles(note, lines, accordFileName, factureFileNames, null);
    }

    @Transactional
    public ExpenseNote createExpenseNoteWithFiles(
            ExpenseNote note,
            List<ExpenseLine> lines,
            List<String> fileNames) {
        return createExpenseNoteWithFiles(note, lines, null, fileNames);
    }

    // ========== MANAGER VALIDATION / REJECTION (with currency) ==========
    @Transactional
    public ExpenseNote managerValidateNote(Long noteId, String comment, String managerId, String managerName,
                                           String displayCurrency, Double exchangeRate) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found"));
        note.setStatus(ExpenseStatus.VALIDEE);
        note.setDecisionComment(comment);
        note.setDecidedBy("M:" + managerName);
        note.setManagerId(managerId);
        note.setDecidedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        ExpenseNote saved = noteRepository.save(note);

        String targetCurrency = displayCurrency;
        Double rate = exchangeRate;
        if (targetCurrency == null || targetCurrency.isEmpty()) {
            targetCurrency = userServiceClient.getUserPreferredCurrency(note.getEmployeeId());
        }
        if (rate == null) {
            try {
                rate = currencyService.getExchangeRate(targetCurrency);
            } catch (Exception e) {
                log.warn("Could not fetch exchange rate for {}, using 1.0", targetCurrency);
                rate = 1.0;
            }
        }
        double convertedAmount = note.getTotalAmount() * rate;

        try {
            String employeeEmail = getEmployeeEmail(note.getEmployeeId());
            notificationClient.notifyExpenseApproved(
                    UUID.fromString(note.getEmployeeId()), employeeEmail,
                    "EXP-" + note.getId(),
                    note.getTotalAmount(), convertedAmount, targetCurrency,
                    note.getId(), managerName, comment
            );
        } catch (Exception e) {
            log.error("Failed to send approval notification: {}", e.getMessage());
        }

        notifyAdminsAboutValidatedNote(saved, managerName, targetCurrency, rate);
        checkForOverrunsAfterValidation(saved);
        return saved;
    }

    @Transactional
    public ExpenseNote managerRejectNote(Long noteId, String comment, String managerId, String managerName,
                                         String displayCurrency, Double exchangeRate) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found"));
        note.setStatus(ExpenseStatus.REFUSEE);
        note.setDecisionComment(comment);
        note.setDecidedBy("M:" + managerName);
        note.setManagerId(managerId);
        note.setDecidedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        ExpenseNote saved = noteRepository.save(note);

        String targetCurrency = displayCurrency;
        Double rate = exchangeRate;
        if (targetCurrency == null || targetCurrency.isEmpty()) {
            targetCurrency = userServiceClient.getUserPreferredCurrency(note.getEmployeeId());
        }
        if (rate == null) {
            try {
                rate = currencyService.getExchangeRate(targetCurrency);
            } catch (Exception e) {
                log.warn("Could not fetch exchange rate for {}, using 1.0", targetCurrency);
                rate = 1.0;
            }
        }
        double convertedAmount = note.getTotalAmount() * rate;

        try {
            String employeeEmail = getEmployeeEmail(note.getEmployeeId());
            notificationClient.notifyExpenseRejected(
                    UUID.fromString(note.getEmployeeId()), employeeEmail,
                    "EXP-" + note.getId(),
                    note.getTotalAmount(), convertedAmount, targetCurrency,
                    note.getId(), managerName, comment
            );
        } catch (Exception e) {
            log.error("Failed to send rejection notification: {}", e.getMessage());
        }

        notifyAdminsAboutRejectedNote(saved, managerName, comment);
        return saved;
    }

    // Legacy simple versions (without currency)
    @Transactional
    public ExpenseNote managerValidateNoteSimple(Long noteId, String comment, String managerId, String managerName) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found"));
        note.setStatus(ExpenseStatus.VALIDEE);
        note.setDecisionComment(comment);
        note.setDecidedBy("M:" + managerName);
        note.setManagerId(managerId);
        note.setDecidedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        return noteRepository.save(note);
    }

    @Transactional
    public ExpenseNote managerRejectNoteSimple(Long noteId, String comment, String managerId, String managerName) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found"));
        note.setStatus(ExpenseStatus.REFUSEE);
        note.setDecisionComment(comment);
        note.setDecidedBy("M:" + managerName);
        note.setManagerId(managerId);
        note.setDecidedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        return noteRepository.save(note);
    }

    // ========== ADMIN METHODS (with currency) ==========
    @Transactional
    public ExpenseNote adminRejectNote(Long noteId, String comment, String displayCurrency, Double exchangeRate) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found"));
        if (note.getStatus() != ExpenseStatus.VALIDEE) {
            throw new IllegalStateException("Seules les notes validées peuvent être refusées par l'admin");
        }
        note.setStatus(ExpenseStatus.REFUSEE);
        note.setDecisionComment(comment);
        note.setDecidedBy("Admin");
        note.setDecidedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        ExpenseNote savedNote = noteRepository.save(note);

        String targetCurrency = displayCurrency;
        Double rate = exchangeRate;
        if (targetCurrency == null || targetCurrency.isEmpty()) {
            targetCurrency = userServiceClient.getUserPreferredCurrency(note.getEmployeeId());
        }
        if (rate == null) {
            try {
                rate = currencyService.getExchangeRate(targetCurrency);
            } catch (Exception e) {
                log.warn("Could not fetch exchange rate for {}, using 1.0", targetCurrency);
                rate = 1.0;
            }
        }
        double convertedAmount = note.getTotalAmount() * rate;

        try {
            String employeeEmail = getEmployeeEmail(note.getEmployeeId());
            notificationClient.notifyExpenseRejected(
                    UUID.fromString(note.getEmployeeId()), employeeEmail,
                    "EXP-" + note.getId(),
                    note.getTotalAmount(), convertedAmount, targetCurrency,
                    note.getId(), "Admin", comment
            );
        } catch (Exception e) {
            log.error("Failed to send admin rejection notification: {}", e.getMessage());
        }

        return savedNote;
    }
/*
    @Transactional
    public ExpenseNote adminReimburseNote(Long noteId, String comment) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found"));
        if (note.getStatus() != ExpenseStatus.VALIDEE) {
            throw new IllegalStateException("Seules les notes validées peuvent être remboursées");
        }
        note.setStatus(ExpenseStatus.REMBOURSEE);
        if (comment != null && !comment.trim().isEmpty()) {
            note.setDecisionComment(comment);
        }
        note.setDecidedBy("Admin");
        note.setDecidedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        ExpenseNote savedNote = noteRepository.save(note);
        String employeeEmail = getEmployeeEmail(note.getEmployeeId());
        notificationClient.notifyExpenseReimbursed(
                UUID.fromString(note.getEmployeeId()), employeeEmail,
                "EXP-" + note.getId(), note.getTotalAmount(), note.getId(), "Admin");
        return savedNote;
    }
*/
    // Legacy validate/refuse (simple)
    @Transactional
    public ExpenseNote validateNote(Long noteId) {
        ExpenseNote note = noteRepository.findById(noteId).orElseThrow();
        note.setUpdatedAt(LocalDateTime.now());
        note.setStatus(ExpenseStatus.VALIDEE);
        return noteRepository.save(note);
    }

    @Transactional
    public ExpenseNote validateNote(Long noteId, String comment) {
        ExpenseNote note = noteRepository.findById(noteId).orElseThrow();
        note.setUpdatedAt(LocalDateTime.now());
        note.setStatus(ExpenseStatus.VALIDEE);
        if (comment != null && !comment.trim().isEmpty()) {
            note.setDecisionComment(comment);
            note.setDecidedBy("Manager (Legacy)");
            note.setDecidedAt(LocalDateTime.now());
        }
        return noteRepository.save(note);
    }

    @Transactional
    public ExpenseNote refuseNote(Long noteId, String comment) {
        ExpenseNote note = noteRepository.findById(noteId).orElseThrow();
        note.setStatus(ExpenseStatus.REFUSEE);
        note.setDecisionComment(comment);
        note.setDecidedBy("M:Manager");
        note.setDecidedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        return noteRepository.save(note);
    }

    // ========== CRUD & QUERY METHODS ==========
    public List<ExpenseNote> getNotesByEmployee(String employeeId) {
        return noteRepository.findByEmployeeId(employeeId);
    }

    public List<ExpenseNote> getNotesByStatus(ExpenseStatus status) {
        return noteRepository.findByStatus(status);
    }

    public List<ExpenseNote> getAllNotes() {
        return noteRepository.findAll();
    }

    public List<ExpenseLine> getLines(Long noteId) {
        return lineRepository.findByExpenseNoteId(noteId);
    }

    public ExpenseNote getNoteWithLines(Long noteId) {
        return noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found with ID: " + noteId));
    }

    @Transactional
    public void deleteNote(Long noteId, String employeeId) {
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found with ID: " + noteId));
        if (!note.getEmployeeId().equals(employeeId)) {
            throw new RuntimeException("You are not authorized to delete this note.");
        }
        if (note.getStatus() != ExpenseStatus.EN_ATTENTE) {
            throw new IllegalStateException("Only pending notes can be deleted.");
        }
        if (note.getAccordPath() != null) {
            fileStorageService.deleteFile(note.getEmployeeId(), note.getAccordPath());
        }
        List<ExpenseLine> lines = lineRepository.findByExpenseNoteId(noteId);
        for (ExpenseLine line : lines) {
            if (line.getJustificatifPath() != null) {
                fileStorageService.deleteFile(note.getEmployeeId(), line.getJustificatifPath());
            }
        }
        lineRepository.deleteByExpenseNoteId(noteId);
        noteRepository.delete(note);
    }

    @Transactional
    public ExpenseNote updateExpenseNoteWithFiles(
            Long noteId, String employeeId, ExpenseNote updatedNote, List<ExpenseLine> updatedLines,
            MultipartFile newAccordFile, List<MultipartFile> newFactureFiles) {
        ExpenseNote existingNote = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found with ID: " + noteId));
        if (!existingNote.getEmployeeId().equals(employeeId)) {
            throw new RuntimeException("You are not authorized to modify this note.");
        }
        if (existingNote.getStatus() != ExpenseStatus.EN_ATTENTE) {
            throw new IllegalStateException("Only pending notes can be modified.");
        }
        existingNote.setProjectId(updatedNote.getProjectId());
        existingNote.setUpdatedAt(LocalDateTime.now());

        if (newAccordFile != null && !newAccordFile.isEmpty()) {
            if (existingNote.getAccordPath() != null) {
                fileStorageService.deleteFile(employeeId, existingNote.getAccordPath());
            }
            String newAccordFileName = fileStorageService.storeFile(newAccordFile, employeeId, "accords");
            existingNote.setAccordPath(newAccordFileName);
        }

        List<ExpenseLine> oldLines = lineRepository.findByExpenseNoteId(noteId);
        Map<Long, ExpenseLine> oldLinesMap = oldLines.stream()
                .collect(Collectors.toMap(ExpenseLine::getId, Function.identity()));
        Set<Long> updatedLineIds = updatedLines.stream()
                .map(ExpenseLine::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (ExpenseLine oldLine : oldLines) {
            if (!updatedLineIds.contains(oldLine.getId())) {
                if (oldLine.getJustificatifPath() != null) {
                    fileStorageService.deleteFile(employeeId, oldLine.getJustificatifPath());
                }
                lineRepository.delete(oldLine);
            }
        }

        double total = 0.0;
        for (int i = 0; i < updatedLines.size(); i++) {
            ExpenseLine line = updatedLines.get(i);
            ExpenseLine lineToSave;
            if (line.getId() != null && oldLinesMap.containsKey(line.getId())) {
                lineToSave = oldLinesMap.get(line.getId());
                lineToSave.setCategoryId(line.getCategoryId());
                lineToSave.setAmount(line.getAmount());
                lineToSave.setExpenseDate(line.getExpenseDate());
                lineToSave.setDescription(line.getDescription());
                copyDynamicFields(line, lineToSave);
            } else {
                lineToSave = new ExpenseLine();
                lineToSave.setExpenseNoteId(noteId);
                lineToSave.setCategoryId(line.getCategoryId());
                lineToSave.setAmount(line.getAmount());
                lineToSave.setExpenseDate(line.getExpenseDate());
                lineToSave.setDescription(line.getDescription());
                copyDynamicFields(line, lineToSave);
            }

            MultipartFile fileForThisLine = (newFactureFiles != null && i < newFactureFiles.size()) ? newFactureFiles.get(i) : null;
            if (fileForThisLine != null && !fileForThisLine.isEmpty()) {
                if (lineToSave.getJustificatifPath() != null) {
                    fileStorageService.deleteFile(employeeId, lineToSave.getJustificatifPath());
                }
                String fileName = fileStorageService.storeFile(fileForThisLine, employeeId, "factures");
                lineToSave.setJustificatifPath(fileName);
            }

            if (lineToSave.getExpenseDate() == null) {
                lineToSave.setExpenseDate(LocalDate.now());
            }
            validateExpenseDate(lineToSave.getExpenseDate());

            if (lineToSave.getId() == null) {
                insertExpenseLineWithDynamicColumns(lineToSave);
            } else {
                updateExpenseLineWithDynamicColumns(lineToSave);
            }
            total += lineToSave.getAmount();
        }

        existingNote.setTotalAmount(total);
        ExpenseNote savedNote = noteRepository.save(existingNote);

        for (ExpenseLine line : updatedLines) {
            List<ExpenseLine> savedLines = lineRepository.findByExpenseNoteId(savedNote.getId());
            for (ExpenseLine savedLine : savedLines) {
                if (savedLine.getCategoryId().equals(line.getCategoryId())
                        && Math.abs(savedLine.getAmount() - line.getAmount()) < 0.01
                        && Objects.equals(savedLine.getDescription(), line.getDescription())) {
                    checkCategoryLimit(savedLine, savedNote.getEmployeeId(), savedNote.getId(), savedNote.getProjectId());
                    break;
                }
            }
        }
        checkProjectBudget(savedNote, savedNote.getId());
        return savedNote;
    }

    public List<ExpenseNote> getNotesByDepartment(Long departmentId) {
        List<Project> departmentProjects = projectRepository.findByDepartmentId(departmentId);
        if (departmentProjects.isEmpty()) return List.of();
        List<Long> projectIds = departmentProjects.stream().map(Project::getId).collect(Collectors.toList());
        return noteRepository.findByProjectIdIn(projectIds);
    }

    public List<ExpenseNote> getNotesForManager(String managerId, Long departmentId) {
        return getNotesByDepartment(departmentId);
    }

    // ========== DYNAMIC COLUMN METHODS ==========
    private void insertExpenseLineWithDynamicColumns(ExpenseLine line) {
        List<String> allColumns = migrationService.getAllColumns();
        List<String> columnsToInsert = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (String column : allColumns) {
            Object value = getValueForColumn(line, column);
            if (value != null) {
                columnsToInsert.add(column);
                params.add(value);
            }
        }
        if (!columnsToInsert.contains("expense_note_id") && line.getExpenseNoteId() != null) {
            columnsToInsert.add("expense_note_id");
            params.add(line.getExpenseNoteId());
        }
        if (!columnsToInsert.isEmpty()) {
            StringBuilder sql = new StringBuilder("INSERT INTO expense_lines (");
            StringBuilder values = new StringBuilder("VALUES (");
            for (int i = 0; i < columnsToInsert.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                    values.append(", ");
                }
                sql.append(columnsToInsert.get(i));
                values.append("?");
            }
            sql.append(") ").append(values).append(")");
            jdbcTemplate.update(sql.toString(), params.toArray());
        }
    }

    private void updateExpenseLineWithDynamicColumns(ExpenseLine line) {
        List<String> allColumns = migrationService.getAllColumns();
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (String column : allColumns) {
            Object value = getValueForColumn(line, column);
            if (value != null) {
                setClauses.add(column + " = ?");
                params.add(value);
            }
        }
        if (!setClauses.isEmpty()) {
            String sql = "UPDATE expense_lines SET " + String.join(", ", setClauses) + " WHERE id = ?";
            params.add(line.getId());
            jdbcTemplate.update(sql, params.toArray());
        }
    }

    private boolean isColumnOfType(String columnName, String targetType) {
        if (!columnTypeCache.containsKey(columnName)) {
            try {
                String sql = "SELECT data_type FROM information_schema.columns WHERE table_name = 'expense_lines' AND column_name = ?";
                String dataType = jdbcTemplate.queryForObject(sql, String.class, columnName);
                columnTypeCache.put(columnName, dataType != null ? dataType.toLowerCase() : "unknown");
            } catch (Exception e) {
                columnTypeCache.put(columnName, "unknown");
            }
        }
        String dataType = columnTypeCache.get(columnName);
        return dataType != null && dataType.contains(targetType.toLowerCase());
    }

    private LocalDate convertToLocalDate(Object value, String columnName) {
        if (value == null) return null;
        if (value instanceof LocalDate) return (LocalDate) value;
        if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate();
        if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.matches("\\d{4}-\\d{2}-\\d{2}")) {
                try { return LocalDate.parse(str); } catch (DateTimeParseException e) { /* fall through */ }
            } else if (str.matches("\\d{2}/\\d{2}/\\d{4}")) {
                try {
                    String[] parts = str.split("/");
                    return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                } catch (Exception e) { /* fall through */ }
            } else if (str.matches("\\d{2}-\\d{2}-\\d{4}")) {
                try {
                    String[] parts = str.split("-");
                    return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                } catch (Exception e) { /* fall through */ }
            } else {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("[yyyy-MM-dd][dd/MM/yyyy][dd-MM-yyyy]");
                    return LocalDate.parse(str, formatter);
                } catch (Exception e) { /* fall through */ }
            }
        }
        log.warn("Using current date for {} (value: {})", columnName, value);
        return LocalDate.now();
    }

    private Object getValueForColumn(ExpenseLine line, String columnName) {
        Object value = switch (columnName) {
            case "expense_note_id" -> line.getExpenseNoteId();
            case "category_id" -> line.getCategoryId();
            case "amount" -> line.getAmount();
            case "expense_date" -> line.getExpenseDate();
            case "description" -> line.getDescription();
            case "justificatif_path" -> line.getJustificatifPath();
            case "depart" -> line.getDepart();
            case "destination" -> line.getDestination();
            case "transport_type" -> line.getTransportType();
            case "nombre_nuits" -> line.getNombreNuits();
            case "hotel_name" -> line.getHotelName();
            case "nombre_personnes" -> line.getNombrePersonnes();
            case "repas_type" -> line.getRepasType();
            case "kilometrage" -> line.getKilometrage();
            case "vehicule" -> line.getVehicule();
            case "detail" -> line.getDetail();
            case "is_anomaly_depense" -> line.getIsAnomalyDepense();
            case "anomaly_expense_message" -> line.getAnomalyExpenseMessage();
            default -> null;
        };
        if (value == null) {
            value = line.getDynamicField(columnName);
            if (value == null) {
                String camelCaseKey = toCamelCase(columnName);
                value = line.getDynamicField(camelCaseKey);
            }
        }
        if (value != null && isColumnOfType(columnName, "date")) {
            return convertToLocalDate(value, columnName);
        }
        return value;
    }

    private String toCamelCase(String snakeCase) {
        if (snakeCase == null) return null;
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                result.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return result.toString();
    }

    private void copyDynamicFields(ExpenseLine source, ExpenseLine target) {
        target.setDepart(source.getDepart());
        target.setDestination(source.getDestination());
        target.setTransportType(source.getTransportType());
        target.setNombreNuits(source.getNombreNuits());
        target.setHotelName(source.getHotelName());
        target.setNombrePersonnes(source.getNombrePersonnes());
        target.setRepasType(source.getRepasType());
        target.setKilometrage(source.getKilometrage());
        target.setVehicule(source.getVehicule());
        target.setDetail(source.getDetail());
        if (source.getDynamicFields() != null) {
            for (Map.Entry<String, Object> entry : source.getDynamicFields().entrySet()) {
                target.setDynamicField(entry.getKey(), entry.getValue());
            }
        }
    }

    // ========== VALIDATION METHODS ==========
    private void validateExpenseDate(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException("Les dépenses ne sont pas autorisées le week-end");
        }
        String formatted = String.format("%02d-%02d", date.getDayOfMonth(), date.getMonthValue());
        if (TUNISIA_HOLIDAYS.contains(formatted)) {
            throw new IllegalArgumentException("Les dépenses ne sont pas autorisées pendant les jours fériés");
        }
    }

    // ========== HELPER METHODS ==========
    private String getEmployeeEmail(String employeeId) {
        return userServiceClient.getUserEmail(employeeId);
    }

    private String getEmployeeName(String employeeId) {
        return userServiceClient.getUserName(employeeId);
    }

    private String getProjectName(Long projectId) {
        try {
            String url = projectServiceUrl + "/api/projects/" + projectId + "/name";
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            return "Projet #" + projectId;
        }
    }

    private String getManagerIdForProjectDepartment(Long projectId) {
        try {
            String projectUrl = projectServiceUrl + "/api/projects/" + projectId + "/department";
            Long departmentId = restTemplate.getForObject(projectUrl, Long.class);
            if (departmentId == null) return null;
            String managersUrl = userServiceUrl + "/api/users/managers";
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> managers = restTemplate.getForObject(managersUrl, List.class);
            for (Map<String, Object> manager : managers) {
                String managerDeptId = (String) manager.get("departmentId");
                if (managerDeptId != null && managerDeptId.equals(String.valueOf(departmentId))) {
                    return (String) manager.get("id");
                }
            }
        } catch (Exception e) {
            log.warn("Error finding manager for project: {}", e.getMessage());
        }
        return null;
    }

    private void checkCategoryLimit(ExpenseLine line, String employeeId, Long expenseId, Long projectId) {
        try {
            Double plafond = categoryService.getPlafondByCategoryId(line.getCategoryId());
            String categoryName = categoryService.getCategoryName(line.getCategoryId());
            if (plafond != null && line.getAmount() > plafond) {
                String managerId = getManagerIdForProjectDepartment(projectId);
                if (managerId != null) {
                    String managerEmail = getEmployeeEmail(managerId);
                    String employeeName = getEmployeeName(employeeId);
                    notificationClient.notifyCategoryLimitExceeded(
                            UUID.fromString(managerId), managerEmail, employeeName,
                            categoryName, line.getAmount(), plafond, expenseId);
                    log.info("Category limit exceeded notification sent for category: {}", categoryName);
                }
            }
        } catch (Exception e) {
            log.warn("Error checking category limit: {}", e.getMessage());
        }
    }

    private void checkProjectBudget(ExpenseNote note, Long expenseId, String targetCurrency, Double exchangeRate) {
        try {
            if (note.getProjectId() != null) {
                List<ExpenseNote> projectNotes = noteRepository.findByProjectId(note.getProjectId());
                double totalExistingExpenses = projectNotes.stream()
                        .filter(n -> n.getStatus() == ExpenseStatus.VALIDEE || n.getStatus() == ExpenseStatus.REMBOURSEE)
                        .filter(n -> !n.getId().equals(note.getId()))
                        .mapToDouble(ExpenseNote::getTotalAmount).sum();
                String projectUrl = projectServiceUrl + "/api/projects/public/" + note.getProjectId();
                ResponseEntity<Map> projectResponse = restTemplate.getForEntity(projectUrl, Map.class);
                Map<String, Object> project = projectResponse.getBody();
                if (project != null) {
                    Double budget = (Double) project.get("budget");
                    if (budget != null && budget > 0) {
                        double noteAmount = note.getTotalAmount();
                        double remainingBudget = budget - totalExistingExpenses;
                        boolean isNoteExcessive = noteAmount > budget;
                        boolean willExceedBudget = noteAmount > remainingBudget;
                        if (isNoteExcessive || willExceedBudget) {
                            String managerId = getManagerIdForProjectDepartment(note.getProjectId());
                            if (managerId != null) {
                                String alertType = isNoteExcessive ? "NOTE_EXCESSIVE" : "BUDGET_OVERUN";
                                String managerEmail = getEmployeeEmail(managerId);
                                String employeeName = getEmployeeName(note.getEmployeeId());
                                String projectName = (String) project.get("name");

                                String currency = (targetCurrency != null) ? targetCurrency : "TND";
                                double rate = (exchangeRate != null) ? exchangeRate : 1.0;
                                double convertedAmount = noteAmount * rate;
                                double convertedRemaining = remainingBudget * rate;

                                notificationClient.notifyBudgetLimitExceeded(
                                        UUID.fromString(managerId), managerEmail, projectName,
                                        employeeName,
                                        noteAmount, convertedAmount, currency, convertedRemaining,
                                        expenseId, alertType
                                );
                                log.info("Budget limit notification sent for project {} with currency {}", note.getProjectId(), currency);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error checking project budget: {}", e.getMessage());
        }
    }

    private void checkProjectBudget(ExpenseNote note, Long expenseId) {
        String managerId = getManagerIdForProjectDepartment(note.getProjectId());
        String currency = "TND";
        double rate = 1.0;
        if (managerId != null) {
            String pref = userServiceClient.getUserPreferredCurrency(managerId);
            if (pref != null && !pref.isEmpty()) {
                currency = pref;
                try {
                    rate = currencyService.getExchangeRate(currency);
                } catch (Exception e) {
                    log.warn("Could not fetch exchange rate for {}, using 1.0", currency);
                }
            }
        }
        checkProjectBudget(note, expenseId, currency, rate);
    }

    private void checkForOverrunsAfterValidation(ExpenseNote note) {
        checkProjectBudgetOverrun(note);
        checkCategoryLimitOverruns(note);
    }

    private void checkProjectBudgetOverrun(ExpenseNote note) {
        try {
            if (note.getProjectId() != null) {
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Employee-Id", note.getEmployeeId());
                HttpEntity<?> entity = new HttpEntity<>(headers);
                String projectUrl = projectServiceUrl + "/api/projects/" + note.getProjectId();
                ResponseEntity<Map> projectResponse = restTemplate.exchange(projectUrl, HttpMethod.GET, entity, Map.class);
                Map<String, Object> project = projectResponse.getBody();
                if (project != null) {
                    Double budget = (Double) project.get("budget");
                    if (budget != null && budget > 0) {
                        List<ExpenseNote> projectNotes = noteRepository.findByProjectId(note.getProjectId());
                        double totalExistingExpenses = projectNotes.stream()
                                .filter(n -> n.getStatus() == ExpenseStatus.VALIDEE || n.getStatus() == ExpenseStatus.REMBOURSEE)
                                .filter(n -> !n.getId().equals(note.getId()))
                                .mapToDouble(ExpenseNote::getTotalAmount).sum();
                        double remainingBudget = budget - totalExistingExpenses;
                        if (note.getTotalAmount() > remainingBudget) {
                            String adminsUrl = userServiceUrl + "/api/users/admins";
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> admins = restTemplate.getForObject(adminsUrl, List.class);
                            if (admins != null) {
                                String projectName = (String) project.get("name");
                                String employeeName = getEmployeeName(note.getEmployeeId());
                                for (Map<String, Object> admin : admins) {
                                    String adminId = (String) admin.get("id");
                                    String adminEmail = (String) admin.get("email");
                                    if (adminId != null && adminEmail != null) {
                                        notificationClient.notifyAdminBudgetOverrun(
                                                UUID.fromString(adminId), adminEmail, projectName,
                                                employeeName, note.getTotalAmount(), remainingBudget, note.getId());
                                        log.info("Admin notified for budget overrun");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error checking project budget overrun: {}", e.getMessage());
        }
    }

    private void checkCategoryLimitOverruns(ExpenseNote note) {
        try {
            List<ExpenseLine> lines = lineRepository.findByExpenseNoteId(note.getId());
            for (ExpenseLine line : lines) {
                Double plafond = categoryService.getPlafondByCategoryId(line.getCategoryId());
                String categoryName = categoryService.getCategoryName(line.getCategoryId());
                if (plafond != null && plafond > 0) {
                    List<ExpenseNote> userNotes = noteRepository.findByEmployeeId(note.getEmployeeId());
                    double totalExistingForCategory = 0.0;
                    for (ExpenseNote userNote : userNotes) {
                        if (!userNote.getId().equals(note.getId())) {
                            List<ExpenseLine> userLines = lineRepository.findByExpenseNoteId(userNote.getId());
                            totalExistingForCategory += userLines.stream()
                                    .filter(l -> l.getCategoryId().equals(line.getCategoryId()))
                                    .mapToDouble(ExpenseLine::getAmount).sum();
                        }
                    }
                    double remainingLimit = plafond - totalExistingForCategory;
                    if (line.getAmount() > remainingLimit) {
                        String adminsUrl = userServiceUrl + "/api/users/admins";
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> admins = restTemplate.getForObject(adminsUrl, List.class);
                        if (admins != null) {
                            String employeeName = getEmployeeName(note.getEmployeeId());
                            for (Map<String, Object> admin : admins) {
                                String adminId = (String) admin.get("id");
                                String adminEmail = (String) admin.get("email");
                                if (adminId != null && adminEmail != null) {
                                    notificationClient.notifyAdminCategoryLimitOverrun(
                                            UUID.fromString(adminId), adminEmail, categoryName,
                                            employeeName, line.getAmount(), remainingLimit, note.getId());
                                    log.info("Admin notified for category limit overrun");
                                }
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error checking category limit overruns: {}", e.getMessage());
        }
    }

    private void notifyAdminsAboutValidatedNote(ExpenseNote note, String managerName, String targetCurrency, Double exchangeRate) {
        try {
            String adminsUrl = userServiceUrl + "/api/users/admins";
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> admins = restTemplate.getForObject(adminsUrl, List.class);
            if (admins == null || admins.isEmpty()) return;

            String employeeName = getEmployeeName(note.getEmployeeId());
            String expenseReference = "EXP-" + note.getId();
            String currency = targetCurrency != null ? targetCurrency : "TND";
            double rate = exchangeRate != null ? exchangeRate : 1.0;
            double convertedAmount = note.getTotalAmount() * rate;

            for (Map<String, Object> admin : admins) {
                String adminId = (String) admin.get("id");
                String adminEmail = (String) admin.get("email");
                if (adminId != null && adminEmail != null) {
                    notificationClient.notifyAdminExpenseValidated(
                            UUID.fromString(adminId), adminEmail, employeeName, expenseReference,
                            note.getTotalAmount(), convertedAmount, currency, note.getId(), managerName);
                    log.info("Admin {} notified for validated expense with amount {} {}", adminId, convertedAmount, currency);
                }
            }
        } catch (Exception e) {
            log.error("Failed to notify admins about validated note: {}", e.getMessage());
        }
    }

    private void notifyAdminsAboutRejectedNote(ExpenseNote note, String managerName, String reason) {
        try {
            String adminsUrl = userServiceUrl + "/api/users/admins";
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> admins = restTemplate.getForObject(adminsUrl, List.class);
            if (admins == null || admins.isEmpty()) return;
            String employeeName = getEmployeeName(note.getEmployeeId());
            String expenseReference = "EXP-" + note.getId();
            for (Map<String, Object> admin : admins) {
                String adminId = (String) admin.get("id");
                String adminEmail = (String) admin.get("email");
                if (adminId != null && adminEmail != null) {
                    notificationClient.notifyAdminExpenseRejected(
                            UUID.fromString(adminId), adminEmail, employeeName,
                            expenseReference, note.getTotalAmount(), note.getId(), managerName, reason);
                    log.info("Admin notified for rejected expense");
                }
            }
        } catch (Exception e) {
            log.error("Failed to notify admins about rejected note: {}", e.getMessage());
        }
    }

    public double calculateConsumedBudget(Long projectId) {
        return noteRepository.findByProjectId(projectId).stream()
                .filter(n -> n.getStatus() == ExpenseStatus.VALIDEE || n.getStatus() == ExpenseStatus.REMBOURSEE)
                .mapToDouble(ExpenseNote::getTotalAmount).sum();
    }

    public double calculateConsumedBudgetExcludingRefused(Long projectId) {
        return noteRepository.findByProjectId(projectId).stream()
                .filter(n -> n.getStatus() != ExpenseStatus.REFUSEE)
                .mapToDouble(ExpenseNote::getTotalAmount).sum();
    }

    // ========== INTERNAL NOTES ==========
    @Transactional
    public ExpenseNoteInternalHistory addInternalNote(Long expenseNoteId, String authorId,
                                                      String authorName, String authorRole,
                                                      String content) {
        log.info("addInternalNote called: expenseNoteId={}, authorId={}, authorName={}, role={}, content={}",
                expenseNoteId, authorId, authorName, authorRole, content);
        if (expenseNoteId == null) {
            throw new IllegalArgumentException("expenseNoteId cannot be null");
        }
        if (!noteRepository.existsById(expenseNoteId)) {
            throw new RuntimeException("Expense note #" + expenseNoteId + " does not exist");
        }
        if (authorId == null) authorId = "unknown";
        if (authorName == null || authorName.isBlank()) authorName = authorId;
        if (authorRole == null) authorRole = "MANAGER";
        if (content == null) content = "";

        ExpenseNoteInternalHistory history = ExpenseNoteInternalHistory.builder()
                .expenseNoteId(expenseNoteId)
                .authorId(authorId)
                .authorName(authorName)
                .authorRole(authorRole)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
        try {
            sendInternalNoteNotification(expenseNoteId, authorId, authorName, authorRole, content);
            return internalHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("Error saving internal note", e);
            throw new RuntimeException("Cannot save internal note: " + e.getMessage(), e);
        }
    }
    private void sendInternalNoteNotification(Long expenseNoteId, String authorId,
                                              String authorName, String authorRole,
                                              String content) {
        try {
            ExpenseNote note = noteRepository.findById(expenseNoteId).orElse(null);
            if (note == null) {
                log.warn("Impossible d'envoyer la notification : note de frais #{} introuvable", expenseNoteId);
                return;
            }

            String expenseReference = "EXP-" + note.getId();

            // Si l'auteur est un manager -> notifier tous les admins
            if ("MANAGER".equalsIgnoreCase(authorRole)) {
                String adminsUrl = userServiceUrl + "/api/users/admins";
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> admins = restTemplate.getForObject(adminsUrl, List.class);
                if (admins != null && !admins.isEmpty()) {
                    int count = 0;
                    for (Map<String, Object> admin : admins) {
                        String adminId = (String) admin.get("id");
                        String adminEmail = (String) admin.get("email");
                        if (adminId != null && adminEmail != null && !adminId.equals(authorId)) {
                            notificationClient.notifyInternalNoteAdded(
                                    UUID.fromString(adminId), adminEmail,
                                    authorName, authorRole, expenseReference, content,
                                    expenseNoteId
                            );
                            count++;
                            log.info("📨 Notification interne envoyée à l'admin {} ({}) pour la note {} par le manager {}",
                                    adminNameFromMap(admin), adminEmail, expenseReference, authorName);
                        }
                    }
                    log.info("✅ {} notification(s) interne(s) envoyée(s) aux administrateurs pour la note {}", count, expenseReference);
                } else {
                    log.info("Aucun admin trouvé, aucune notification envoyée pour la note interne de manager sur {}", expenseReference);
                }
            }
            // Si l'auteur est un admin -> notifier le manager du projet
            else if ("ADMIN".equalsIgnoreCase(authorRole)) {
                String managerId = getManagerIdForProjectDepartment(note.getProjectId());
                if (managerId != null && !managerId.equals(authorId)) {
                    String managerEmail = getEmployeeEmail(managerId);
                    String managerName = getEmployeeName(managerId);
                    notificationClient.notifyInternalNoteAdded(
                            UUID.fromString(managerId), managerEmail,
                            authorName, authorRole, expenseReference, content,
                            expenseNoteId
                    );
                    log.info("📨 Notification interne envoyée au manager {} ({}) pour la note {} par l'admin {}",
                            managerName, managerEmail, expenseReference, authorName);
                } else if (managerId == null) {
                    log.info("Aucun manager trouvé pour le projet #{}, notification non envoyée", note.getProjectId());
                } else {
                    log.info("L'admin {} est le manager du projet, pas de notification à soi-même", authorName);
                }
            } else {
                log.debug("Rôle '{}' non pris en charge pour les notifications internes (seul MANAGER ou ADMIN déclenche une notification)", authorRole);
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de la notification pour note interne sur EXP-{} : {}", expenseNoteId, e.getMessage(), e);
        }
    }

    // Petit helper pour extraire le nom d'un admin depuis la map
    private String adminNameFromMap(Map<String, Object> admin) {
        Object name = admin.get("name");
        if (name != null) return name.toString();
        Object username = admin.get("username");
        if (username != null) return username.toString();
        Object id = admin.get("id");
        return id != null ? id.toString() : "admin inconnu";
    }
    public List<ExpenseNoteInternalHistory> getInternalHistory(Long expenseNoteId) {
        return internalHistoryRepository.findByExpenseNoteIdOrderByCreatedAtAsc(expenseNoteId);
    }

    // ========== FAISS METHODS (with reference extraction) ==========
    private void addAccordToFaissIndex(String text, String filename, String absolutePath, String accordReference) {
        try {
            String url = faissAddAccordUrl;
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
            log.info("Accord reference found (pattern 1): {}", m1.group(1).trim());
            return m1.group(1).trim();
        }

        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile(
                "\\b([A-Z]{2,4}-\\d{4}-\\d{2,6})\\b"
        );
        java.util.regex.Matcher m2 = p2.matcher(accordOcrText);
        if (m2.find()) {
            log.info("Accord reference found (pattern 2): {}", m2.group(1).trim());
            return m2.group(1).trim();
        }

        log.warn("No accord reference found in OCR text");
        return null;
    }

    private String callFullAnalysis(ExpenseNote note, List<ExpenseLine> lines, String accordFileName) {
        try {
            String accordFullPath = fileStorageService.getFullPath(note.getEmployeeId(), accordFileName);
            File accordFile = new File(accordFullPath);
            if (!accordFile.exists()) {
                log.error("Accord file not found: {}", accordFullPath);
                return null;
            }
            Map<String, Object> formData = new HashMap<>();
            formData.put("employeeId", note.getEmployeeId());
            formData.put("employeeName", getEmployeeName(note.getEmployeeId()));
            formData.put("employeeMatricule", "Non renseigné");
            formData.put("projectId", note.getProjectId());
            formData.put("projectName", getProjectName(note.getProjectId()));
            formData.put("projectDepartment", "");
            formData.put("noteDescription", note.getNoteDescription());

            List<Map<String, Object>> expenseLines = new ArrayList<>();
            for (ExpenseLine line : lines) {
                Map<String, Object> lineData = new HashMap<>();
                lineData.put("categoryId", line.getCategoryId());
                lineData.put("categoryName", categoryService.getCategoryName(line.getCategoryId()));
                lineData.put("amount", line.getAmount());
                lineData.put("expenseDate", line.getExpenseDate().toString());
                lineData.put("description", line.getDescription());
                expenseLines.add(lineData);
            }
            formData.put("expenseLines", expenseLines);
            formData.put("expenseDates", lines.stream().map(l -> l.getExpenseDate().toString()).collect(Collectors.toList()));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(accordFile));
            body.add("form_data", new ObjectMapper().writeValueAsString(formData));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String response = restTemplate.postForObject(fullAnalysisUrl, requestEntity, String.class);
            log.info("Full analysis obtained for note #{}", note.getId());
            return response;
        } catch (Exception e) {
            log.error("Full analysis failed: {}", e.getMessage());
            return null;
        }
    }

    private String extractAccordReferenceFromStructuredJson(Map<String, Object> structuredJson) {
        if (structuredJson == null) return null;
        Object ref = structuredJson.get("accord_reference");
        if (ref instanceof String && !((String) ref).isEmpty()) return (String) ref;
        for (String key : Arrays.asList("ref", "reference", "Réf", "Ref")) {
            Object val = structuredJson.get(key);
            if (val instanceof String && !((String) val).isEmpty()) return (String) val;
        }
        return null;
    }

    private void addToFaissIndexWithRef(String text, String filename, String absolutePath, String accordReference) {
        try {
            String url = faissAddUrl;
            Map<String, Object> body = new HashMap<>();
            body.put("text", text);
            body.put("filename", filename);
            body.put("filepath", absolutePath);
            if (accordReference != null && !accordReference.isEmpty()) {
                body.put("accord_reference", accordReference);
            }
            restTemplate.postForObject(url, body, Map.class);
            log.info("✅ Accord indexé dans FAISS : {} (ref: {})", filename, accordReference);
        } catch (Exception e) {
            log.error("❌ Erreur indexation FAISS pour l'accord : {}", e.getMessage());
        }
    }

    private String extractAccordReferenceFromText(String text) {
        if (text == null) return null;

        List<String> patterns = Arrays.asList(
                "R[ée]f(?:érence)?\\s*:\\s*([A-Z0-9\\-_/]{4,30})",
                "\\b([A-Z]{1,3}-\\d{4,6}-[A-Z0-9]{2,10})\\b",
                "\\b([A-Z0-9]{2,5}-\\d{4,6})\\b"
        );

        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }
    @Transactional
    public ExpenseNote adminReimburseNote(Long noteId, String comment) {
        // 1. Récupérer la note
        ExpenseNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found with ID: " + noteId));

        // 2. Vérifier que la note est validée
        if (note.getStatus() != ExpenseStatus.VALIDEE) {
            throw new IllegalStateException("Seules les notes validées peuvent être remboursées");
        }

        // 3. Calculer le montant réellement remboursé (en TND)
        //    Soit la somme des montants remboursés des lignes via ReimbursementService
        Double totalReimbursed = reimbursementService.getTotalReimbursedForNote(noteId);
        if (totalReimbursed == null) {
            // Fallback : remboursement total de la note
            totalReimbursed = note.getTotalAmount() != null ? note.getTotalAmount() : 0.0;
        }

        // 4. Mettre à jour les champs de la note
        note.setReimbursedAmount(totalReimbursed);
        note.setStatus(ExpenseStatus.REMBOURSEE);
        if (comment != null && !comment.trim().isEmpty()) {
            note.setDecisionComment(comment);
        }
        note.setDecidedBy("Admin");
        note.setDecidedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        // 5. Sauvegarder
        ExpenseNote savedNote = noteRepository.save(note);

        // 6. Notification (existe déjà)
        String employeeEmail = getEmployeeEmail(note.getEmployeeId());
        notificationClient.notifyExpenseReimbursed(
                UUID.fromString(note.getEmployeeId()), employeeEmail,
                "EXP-" + note.getId(), note.getTotalAmount(), note.getId(), "Admin");

        return savedNote;
    }
}