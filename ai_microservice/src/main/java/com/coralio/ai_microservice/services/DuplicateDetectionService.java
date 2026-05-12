package com.coralio.ai_microservice.services;

import com.coralio.ai_microservice.model.DuplicateResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.*;
import java.util.HashMap;
import java.util.Map;

@Service
public class DuplicateDetectionService {

    @Value("${uploads.dir:C:\\Users\\MSI\\coralio_backend\\uploads}")
    private String uploadsDir;
    private static final Logger log = LoggerFactory.getLogger(DuplicateDetectionService.class);

    @Value("${python.ai.url:http://localhost:9000}")
    private String pythonBaseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private RestTemplate createLongTimeoutRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60_000);
        factory.setReadTimeout(600_000);
        return new RestTemplate(factory);
    }

    // ========== YOUR ADVANCED METHODS (unchanged) ==========
    public DuplicateResult checkDuplicate(MultipartFile file, String employeeId) throws Exception {
        log.info("📌 checkDuplicate appelé pour file={}, employeeId={}", file.getOriginalFilename(), employeeId);
        RestTemplate longTimeoutRest = createLongTimeoutRestTemplate();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(file.getBytes()) {
            @Override public String getFilename() { return file.getOriginalFilename(); }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> analyzeRequest = new HttpEntity<>(body, headers);

        log.info("📤 Appel /analyze du service Python pour extraire le texte");
        Map<String, Object> analyzeResponse = longTimeoutRest.postForObject(
                pythonBaseUrl + "/analyze", analyzeRequest, Map.class);
        String ocrText = (String) analyzeResponse.get("ocr_text");
        log.info("📥 /analyze a retourné {} caractères OCR", ocrText != null ? ocrText.length() : 0);

        Map<String, String> dupBody = new HashMap<>();
        dupBody.put("text", ocrText);
        dupBody.put("filename", file.getOriginalFilename());
        dupBody.put("filepath", buildFullPath(employeeId, file.getOriginalFilename()));

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> dupRequest = new HttpEntity<>(dupBody, jsonHeaders);

        log.info("📤 Appel /check-duplicate du service Python");
        Map<String, Object> dupResponse = restTemplate.postForObject(
                pythonBaseUrl + "/check-duplicate", dupRequest, Map.class);
        log.info("📥 Réponse /check-duplicate: {}", dupResponse);

        boolean duplicate = (boolean) dupResponse.getOrDefault("duplicate", false);
        double score = Double.parseDouble(dupResponse.get("score").toString());
        String matchedFile = (String) dupResponse.get("file");
        String matchedPath = (String) dupResponse.get("path");
        if (matchedFile == null) matchedFile = "unknown";
        if (matchedPath == null) matchedPath = matchedFile;

        log.info("✅ Résultat final duplicate={}, score={}, matchedPath={}", duplicate, score, matchedPath);
        return new DuplicateResult(duplicate, matchedFile, matchedPath, score);
    }

    public DuplicateResult checkDuplicateFromText(String ocrText, String employeeId, String realFilepath) throws Exception {
        return checkDuplicateFromText(ocrText, employeeId, realFilepath, null);
    }

    public DuplicateResult checkDuplicateFromText(String ocrText, String employeeId,
                                                  String realFilepath, String excludePath) throws Exception {
        log.info("📌 checkDuplicateFromText with excludePath={}", excludePath);
        String checkUrl = pythonBaseUrl + "/check-duplicate";
        Map<String, String> body = new HashMap<>();
        body.put("text", ocrText);
        body.put("filename", Paths.get(realFilepath).getFileName().toString());
        body.put("filepath", realFilepath);
        if (excludePath != null && !excludePath.isBlank()) {
            body.put("exclude_path", excludePath);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

        log.info("📤 Envoi requête à Python: url={}, body={}", checkUrl, body);
        Map<String, Object> dupResponse = restTemplate.postForObject(checkUrl, requestEntity, Map.class);
        log.info("📥 Réponse Python: {}", dupResponse);

        boolean duplicate = (boolean) dupResponse.getOrDefault("duplicate", false);
        double score = Double.parseDouble(dupResponse.get("score").toString());
        String matchedFile = (String) dupResponse.get("file");
        String matchedPath = (String) dupResponse.get("path");
        if (matchedFile == null) matchedFile = "unknown";
        if (matchedPath == null) matchedPath = matchedFile;

        log.info("✅ Résultat duplicate={}, score={}, matchedPath={}", duplicate, score, matchedPath);
        return new DuplicateResult(duplicate, matchedFile, matchedPath, score);
    }

    public DuplicateResult checkDuplicateFromText(String ocrText, String employeeId) throws Exception {
        log.info("📌 checkDuplicateFromText (sans filepath) appelé pour employeeId={}", employeeId);
        String tempFilename = "from_text_" + System.currentTimeMillis() + ".txt";
        String tempFilepath = buildFullPath(employeeId, tempFilename);
        return checkDuplicateFromText(ocrText, employeeId, tempFilepath, null);
    }

    public Map<String, Object> extractFields(MultipartFile file, String fieldsJson) {
        log.info("📌 extractFields appelé pour file={}, fieldsJson={}", file.getOriginalFilename(), fieldsJson);
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> extracted = new HashMap<>();
        response.put("fields", extracted);

        try {
            RestTemplate longTimeoutRest = createLongTimeoutRestTemplate();

            String analyzeUrl = pythonBaseUrl + "/analyze";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return file.getOriginalFilename(); }
            });
            HttpEntity<MultiValueMap<String, Object>> analyzeRequest = new HttpEntity<>(body, headers);

            log.info("📤 Appel /analyze pour extraire le JSON");
            Map<String, Object> analyzeResponse = longTimeoutRest.postForObject(analyzeUrl, analyzeRequest, Map.class);
            if (analyzeResponse == null || !analyzeResponse.containsKey("structured")) {
                response.put("error", "Réponse invalide du service IA");
                log.error("❌ Réponse invalide de /analyze: {}", analyzeResponse);
                return response;
            }

            String ocrText = (String) analyzeResponse.get("ocr_text");
            Map<String, Object> structured = (Map<String, Object>) analyzeResponse.get("structured");
            String humanDescription = (String) analyzeResponse.get("human_description"); // Récupération de la description humaine

            if (structured.containsKey("invoice") && structured.get("invoice") instanceof Map) {
                structured = (Map<String, Object>) structured.get("invoice");
            }
            response.put("ocrText", ocrText);
            response.put("structured", structured);
            if (humanDescription != null) {
                response.put("humanDescription", humanDescription);
            }
            log.info("📥 /analyze retourné ocrText ({} chars) et structured, humanDescription={}", ocrText != null ? ocrText.length() : 0, humanDescription);

            List<String> requestedFields = new ArrayList<>();
            if (fieldsJson != null && !fieldsJson.isBlank()) {
                try {
                    requestedFields = objectMapper.readValue(fieldsJson, new TypeReference<List<String>>() {});
                } catch (Exception e) {
                    requestedFields = Arrays.asList(fieldsJson.split(","));
                }
            }
            log.info("📋 Champs demandés: {}", requestedFields);

            for (String field : requestedFields) {
                String lowerField = field.toLowerCase();
                // Pour les champs de description, on utilise d'abord humanDescription si disponible
                if ((lowerField.equals("description") || lowerField.equals("objet") || lowerField.equals("motif"))
                        && humanDescription != null && !humanDescription.isBlank()) {
                    extracted.put(field, humanDescription);
                    log.info("📝 Champ '{}' alimenté par human_description: {}", field, humanDescription);
                } else {
                    Object value = extractFieldValueGeneric(structured, field);
                    if (value != null && !value.toString().isEmpty()) {
                        extracted.put(field, value);
                    }
                }
            }

            if (requestedFields.contains("expenseDate") && !extracted.containsKey("expenseDate")) {
                Object date = findBestDate(structured);
                if (date != null) extracted.put("expenseDate", date);
            }
            if (requestedFields.contains("amount") && !extracted.containsKey("amount")) {
                Object amount = findBestAmount(structured);
                if (amount != null) extracted.put("amount", amount);
            }
            if (extracted.containsKey("amount")) {
                String rawAmount = getRawAmountValue(structured);
                if (rawAmount != null && !rawAmount.isEmpty()) {
                    String currency = extractCurrencyFromAmount(rawAmount);
                    if (currency != null) extracted.put("currency", currency);
                }
            }
            log.info("✅ Champs extraits: {}", extracted);

        } catch (Exception e) {
            response.put("error", "Service IA indisponible: " + e.getMessage());
            log.error("❌ Erreur lors de extractFields: ", e);
        }
        return response;
    }

    public Map<String, Object> validateReceipt(MultipartFile file,
                                               Double amount,
                                               String expenseDate,
                                               String description,
                                               Long categoryId,
                                               Map<String, Object> additionalFields) throws Exception {
        log.info("📌 validateReceipt appelé pour file={}, amount={}, date={}", file.getOriginalFilename(), amount, expenseDate);
        String analyzeUrl = pythonBaseUrl + "/analyze";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(file.getBytes()) {
            @Override public String getFilename() { return file.getOriginalFilename(); }
        });
        HttpEntity<MultiValueMap<String, Object>> analyzeRequest = new HttpEntity<>(body, headers);
        Map<String, Object> analyzeResponse = restTemplate.postForObject(analyzeUrl, analyzeRequest, Map.class);
        Map<String, Object> receiptData = (Map<String, Object>) analyzeResponse.get("structured");
        log.info("📥 /analyze a extrait le JSON: {}", receiptData);

        Map<String, Object> formData = new HashMap<>();
        if (amount != null) formData.put("amount", amount);
        if (expenseDate != null) formData.put("expenseDate", expenseDate);
        if (description != null) formData.put("description", description);
        if (additionalFields != null) formData.putAll(additionalFields);

        Map<String, Object> validateRequest = new HashMap<>();
        validateRequest.put("receipt_data", receiptData);
        validateRequest.put("form_data", formData);

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> validateEntity = new HttpEntity<>(validateRequest, jsonHeaders);
        String validateUrl = pythonBaseUrl + "/validate";
        log.info("📤 Appel /validate du service Python");
        Map<String, Object> validationResult = restTemplate.postForObject(validateUrl, validateEntity, Map.class);
        log.info("📥 Réponse /validate: {}", validationResult);
        validationResult.put("extractedReceipt", receiptData);
        return validationResult;
    }

    public Map<String, Object> validateFromJson(Map<String, Object> extractedJson) throws Exception {
        log.info("📌 validateFromJson appelé avec JSON: {}", extractedJson);
        String validateUrl = pythonBaseUrl + "/validate";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("receipt_data", extractedJson);
        requestBody.put("merchant_type", null);
        requestBody.put("star_rating", null);
        requestBody.put("location", null);
        requestBody.put("check_in", null);
        requestBody.put("check_out", null);
        requestBody.put("cuisine", null);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        log.info("📤 Appel /validate (depuis JSON) avec body: {}", requestBody);
        Map<String, Object> validationResult = restTemplate.postForObject(validateUrl, requestEntity, Map.class);
        log.info("📥 Réponse /validate-from-json: {}", validationResult);
        return validationResult;
    }

    // ========== ADDITION FOR EXPENSE SERVICE COMPATIBILITY ==========
    public void addToFaissIndex(String text, String filename, String absolutePath, String employeeId) {
        try {
            String url = pythonBaseUrl + "/add-to-index";
            Map<String, Object> body = new HashMap<>();
            body.put("text", text);
            body.put("filename", filename);
            body.put("filepath", absolutePath);
            body.put("employeeId", employeeId);
            restTemplate.postForObject(url, body, Map.class);
            log.info("Added to FAISS with employeeId: {} -> {}", filename, employeeId);
        } catch (Exception e) {
            log.error("FAISS error: {}", e.getMessage());
        }
    }

    // ========== HELPER METHODS ==========
    private String buildFullPath(String employeeId, String filename) {
        if (employeeId == null || employeeId.isBlank()) {
            return Paths.get(uploadsDir, filename).toAbsolutePath().toString();
        }
        String[] subFolders = {"factures", "accords"};
        for (String sub : subFolders) {
            Path path = Paths.get(uploadsDir, employeeId, sub, filename);
            if (Files.exists(path)) {
                log.info("📁 [AI] Fichier trouvé dans: {}", sub);
                return path.toAbsolutePath().toString();
            }
        }
        return Paths.get(uploadsDir, employeeId, "factures", filename).toAbsolutePath().toString();
    }

    private String getRawAmountValue(Map<String, Object> json) {
        Object totalObj = findValueByKeyContaining(json, "total");
        if (totalObj != null) return totalObj.toString();
        Object amountObj = findSurchargeAmount(json);
        if (amountObj != null) return amountObj.toString();
        if (json.containsKey("total")) return json.get("total").toString();
        if (json.containsKey("amount")) return json.get("amount").toString();
        for (String key : Arrays.asList("items", "line_items")) {
            if (json.containsKey(key)) {
                Object items = json.get(key);
                if (items instanceof List && !((List<?>) items).isEmpty()) {
                    Object first = ((List<?>) items).get(0);
                    if (first instanceof Map && ((Map<?, ?>) first).containsKey("price")) {
                        return ((Map<?, ?>) first).get("price").toString();
                    }
                }
            }
        }
        return null;
    }

    private String extractCurrencyFromAmount(String amountStr) {
        if (amountStr == null) return null;
        if (amountStr.contains("$")) return "USD";
        if (amountStr.contains("€")) return "EUR";
        if (amountStr.contains("£")) return "GBP";
        if (amountStr.contains("¥")) return "JPY";
        String upper = amountStr.toUpperCase();
        if (upper.contains("USD")) return "USD";
        if (upper.contains("EUR")) return "EUR";
        if (upper.contains("TND")) return "TND";
        return null;
    }

    // ------------------------------------------------------------
    // CORRECTED extractFieldValueGeneric – handles depart, description, transportType properly
    // ------------------------------------------------------------
    private Object extractFieldValueGeneric(Map<String, Object> json, String fieldName) {
        String lowerField = fieldName.toLowerCase();
        // amount, total, montant
        if (lowerField.equals("amount") || lowerField.equals("montant") || lowerField.equals("total")) {
            return findBestAmount(json);
        }
        // date fields
        if (lowerField.equals("expensedate") || lowerField.equals("date") || lowerField.equals("date_facture")) {
            return findBestDate(json);
        }
        // description – use improved findBestDescription (fallback si human_description non utilisé)
        if (lowerField.equals("description") || lowerField.equals("objet") || lowerField.equals("motif")) {
            return findBestDescription(json);
        }
        // DEPART – priority: origin → departure (without "_time") → similar
        if (lowerField.equals("depart") || lowerField.equals("departure") || lowerField.equals("departure_location")) {
            Object origin = findExactKey(json, "origin");
            if (origin != null && !origin.toString().isBlank()) return origin;
            Object departurePlace = findValueByKeyContainingExceptTime(json, "departure");
            if (departurePlace != null && !departurePlace.toString().isBlank()) return departurePlace;
            Object val = findValueBySimilarKey(json, normalizeKey("origin"));
            if (val != null) return val;
            return null;
        }
        // DESTINATION – arrival city
        if (lowerField.equals("destination") || lowerField.equals("arrival") || lowerField.equals("arrival_city")) {
            Object dest = findExactKey(json, "destination");
            if (dest != null) return dest;
            Object arrival = findValueBySimilarKey(json, normalizeKey("arrival"));
            if (arrival != null) return arrival;
            return null;
        }
        // TRANSPORT TYPE – deduce from train_number, flight_number, or document_type
        if (lowerField.equals("transporttype") || lowerField.equals("transport")) {
            Object explicit = findExactKey(json, "transport_type");
            if (explicit != null) return explicit;
            if (findExactKey(json, "train_number") != null) return "train";
            if (findValueByKeyContaining(json, "train") != null) return "train";
            if (findExactKey(json, "flight_number") != null) return "flight";
            if (findValueByKeyContaining(json, "flight") != null) return "flight";
            Object docType = json.get("document_type");
            if (docType != null) {
                String dt = docType.toString().toLowerCase();
                if (dt.contains("train")) return "train";
                if (dt.contains("flight")) return "flight";
            }
            return null;
        }
        // generic similar key for any other field
        String normalizedTarget = normalizeKey(lowerField);
        Object rawValue = findValueBySimilarKey(json, normalizedTarget);
        if (rawValue != null) return postProcessValue(rawValue, fieldName);
        return null;
    }

    // Helper: find key containing target but exclude '_time' (for depart)
    private Object findValueByKeyContainingExceptTime(Map<String, Object> map, String target) {
        String targetNorm = normalizeKey(target);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String keyNorm = normalizeKey(entry.getKey());
            if (keyNorm.contains(targetNorm) && !entry.getKey().toLowerCase().contains("_time")) {
                return entry.getValue();
            }
            Object value = entry.getValue();
            if (value instanceof Map) {
                Object found = findValueByKeyContainingExceptTime((Map<String, Object>) value, target);
                if (found != null) return found;
            }
            if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        Object found = findValueByKeyContainingExceptTime((Map<String, Object>) item, target);
                        if (found != null) return found;
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------
    // IMPROVED findBestDescription – uses short description first,
    // then document_type (if not generic), then construction, then fallback
    // ------------------------------------------------------------
    private Object findBestDescription(Map<String, Object> json) {
        // 1) Recherche d'une description courte et explicite (objet, payment_description, title)
        Object shortDesc = findShortDescription(json);
        if (shortDesc != null && !isTooLongOrLegal(shortDesc.toString())) {
            return shortDesc;
        }

        // 2) Si aucune bonne description courte, essayons le document_type (sauf s'il est générique)
        Object docType = json.get("document_type");
        if (docType != null && !isGenericDocumentType(docType.toString())) {
            return docType.toString();
        }

        // 3) Construction à partir de ticket_type + origine + destination
        Object ticketType = json.get("ticket_type");
        Object origin = json.get("origin");
        Object dest = json.get("destination");
        if (ticketType != null && origin != null && dest != null) {
            return ticketType.toString() + " : " + origin + " → " + dest;
        }
        if (origin != null && dest != null) {
            return "Voyage de " + origin + " à " + dest;
        }

        // 4) Fallback : description, name, items, surcharge
        Object desc = findValueByKeyContaining(json, "description");
        if (desc != null && !desc.toString().isEmpty()) return desc;
        Object name = json.get("name");
        if (name != null && !name.toString().toLowerCase().contains("jones")) return name;
        if (json.containsKey("items")) {
            Object itemsObj = json.get("items");
            if (itemsObj instanceof List && !((List<?>) itemsObj).isEmpty()) {
                Object first = ((List<?>) itemsObj).get(0);
                if (first instanceof Map) {
                    Object itemName = ((Map<?, ?>) first).get("name");
                    if (itemName != null) return itemName.toString();
                }
            }
        }
        Object surchargeDesc = findSurchargeDescription(json);
        if (surchargeDesc != null) return surchargeDesc;
        return "receipt";
    }

    // Helper to extract a short description first (payment_description, objet, title)
    private Object findShortDescription(Map<String, Object> json) {
        Object paymentDesc = findValueByKeyContaining(json, "payment_description");
        if (paymentDesc != null && !paymentDesc.toString().isEmpty()) return paymentDesc;
        Object objet = findValueByKeyContaining(json, "objet");
        if (objet != null && !objet.toString().isEmpty()) return objet;
        Object title = findValueByKeyContaining(json, "title");
        if (title != null && !title.toString().isEmpty()) return title;
        return null;
    }

    // Check if text is too long (>200 chars) or contains legal keywords
    private boolean isTooLongOrLegal(String text) {
        if (text.length() > 200) return true;
        String lower = text.toLowerCase();
        String[] legalKeywords = {"conditions générales", "terms and conditions", "legal notice",
                "avertissement", "disclaimer", "sous réserve de", "conformément à",
                "modalités", "annexe", "contrat", "clause"};
        for (String kw : legalKeywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    // Check if document_type is generic (should be ignored for description)
    private boolean isGenericDocumentType(String docType) {
        String lower = docType.toLowerCase();
        return lower.equals("other") || lower.equals("receipt") || lower.equals("unknown") || lower.equals("document");
    }

    // ------------------------------------------------------------
    // Existing helper methods (unchanged)
    // ------------------------------------------------------------
    private Object findValueBySimilarKey(Map<String, Object> map, String targetNorm) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String keyNorm = normalizeKey(entry.getKey());
            if (keyNorm.contains(targetNorm) || targetNorm.contains(keyNorm)) {
                return entry.getValue();
            }
            Object value = entry.getValue();
            if (value instanceof Map) {
                Object found = findValueBySimilarKey((Map<String, Object>) value, targetNorm);
                if (found != null) return found;
            }
            if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        Object found = findValueBySimilarKey((Map<String, Object>) item, targetNorm);
                        if (found != null) return found;
                    }
                }
            }
        }
        return null;
    }

    private String normalizeKey(String key) {
        String normalized = key.toLowerCase()
                .replaceAll("[éèêë]", "e")
                .replaceAll("[àâä]", "a")
                .replaceAll("[ùûü]", "u")
                .replaceAll("[ôö]", "o")
                .replaceAll("[ç]", "c")
                .replaceAll("[\\s_-]", "")
                .replaceAll("[^a-z0-9]", "");
        return normalized;
    }

    private Object postProcessValue(Object raw, String fieldName) {
        String lowerField = fieldName.toLowerCase();
        String str = raw.toString().trim();
        if (lowerField.contains("amount") || lowerField.contains("montant") || lowerField.contains("total") ||
                lowerField.contains("prix") || lowerField.contains("cout") || lowerField.contains("price")) {
            return normalizeAmount(str);
        }
        if (lowerField.contains("date")) {
            return normalizeDate(str);
        }
        if (lowerField.contains("nuit") || lowerField.contains("personne") || lowerField.contains("km") ||
                lowerField.contains("quantite") || lowerField.contains("litre")) {
            String digits = str.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) return digits;
        }
        if (lowerField.equals("destination") || lowerField.equals("arrival") ||
                lowerField.equals("depart") || lowerField.equals("departure")) {
            return str.replaceAll(",$", "").trim();
        }
        return str;
    }

    private Object findBestAmount(Map<String, Object> json) {
        Object totalValue = findExactKey(json, "total");
        if (totalValue != null) {
            String norm = normalizeAmount(totalValue.toString());
            if (norm != null) return norm;
        }
        totalValue = findValueByKeyContaining(json, "total");
        if (totalValue != null) {
            String norm = normalizeAmount(totalValue.toString());
            if (norm != null) return norm;
        }
        Object surcharge = findSurchargeAmount(json);
        if (surcharge != null) return surcharge;
        for (String itemsKey : Arrays.asList("items", "line_items")) {
            if (json.containsKey(itemsKey)) {
                Object items = json.get(itemsKey);
                if (items instanceof List) {
                    double sum = 0;
                    for (Object item : (List<?>) items) {
                        if (item instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) item;
                            Object price = map.get("price");
                            if (price == null) price = map.get("amount");
                            if (price != null) {
                                Double val = parseAmount(price.toString());
                                if (val != null) sum += val;
                            }
                        }
                    }
                    if (sum > 0) return String.format("%.2f", sum);
                }
            }
        }
        return null;
    }

    private Object findExactKey(Map<String, Object> map, String targetKey) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(targetKey)) {
                return entry.getValue();
            }
            Object value = entry.getValue();
            if (value instanceof Map) {
                Object found = findExactKey((Map<String, Object>) value, targetKey);
                if (found != null) return found;
            } else if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        Object found = findExactKey((Map<String, Object>) item, targetKey);
                        if (found != null) return found;
                    }
                }
            }
        }
        return null;
    }

    private Object findSurchargeAmount(Map<String, Object> json) {
        return findSurchargeValueRecursive(json, "amount");
    }

    private Object findSurchargeDescription(Map<String, Object> json) {
        return findSurchargeValueRecursive(json, "description");
    }

    private Object findSurchargeValueRecursive(Object obj, String targetKey) {
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            if (map.containsKey("amount") && map.containsKey("description")) {
                return map.get(targetKey);
            }
            for (Object value : map.values()) {
                Object found = findSurchargeValueRecursive(value, targetKey);
                if (found != null) return found;
            }
        } else if (obj instanceof List) {
            for (Object item : (List<?>) obj) {
                Object found = findSurchargeValueRecursive(item, targetKey);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Object findBestDate(Map<String, Object> json) {
        if (json.containsKey("date")) return normalizeDate(json.get("date").toString());
        if (json.containsKey("issue_date")) return normalizeDate(json.get("issue_date").toString());
        return findValueByKeyContaining(json, "date");
    }

    private Object findValueByKeyContaining(Map<String, Object> map, String target) {
        String targetNorm = normalizeKey(target);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String keyNorm = normalizeKey(entry.getKey());
            if (keyNorm.contains(targetNorm)) {
                return entry.getValue();
            }
            Object value = entry.getValue();
            if (value instanceof Map) {
                Object found = findValueByKeyContaining((Map<String, Object>) value, target);
                if (found != null) return found;
            }
            if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        Object found = findValueByKeyContaining((Map<String, Object>) item, target);
                        if (found != null) return found;
                    }
                }
            }
        }
        return null;
    }

    private String normalizeAmount(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("[^\\d.,-]", "").trim();
        if (cleaned.isEmpty()) return null;
        cleaned = cleaned.replace(',', '.');
        try {
            double val = Double.parseDouble(cleaned);
            return String.format("%.2f", val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseAmount(String raw) {
        String cleaned = raw.replaceAll("[^\\d.,-]", "").trim();
        if (cleaned.isEmpty()) return null;
        cleaned = cleaned.replace(',', '.');
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeDate(String raw) {
        if (raw == null) return null;
        String[] patterns = {
                "ddMMMyy", "dd MMM yy", "dd-MMM-yy",
                "MMMM d,yyyy", "MMMM dd, yyyy", "MM/dd/yyyy", "dd/MM/yyyy", "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                java.time.format.DateTimeFormatter formatter =
                        java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.ENGLISH);
                java.time.LocalDate date = java.time.LocalDate.parse(raw, formatter);
                return date.toString();
            } catch (Exception ignored) {}
        }
        return raw;
    }
}