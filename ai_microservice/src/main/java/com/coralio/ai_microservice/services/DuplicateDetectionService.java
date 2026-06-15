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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            String humanDescription = (String) analyzeResponse.get("human_description");

            if (structured.containsKey("invoice") && structured.get("invoice") instanceof Map) {
                structured = (Map<String, Object>) structured.get("invoice");
            }
            response.put("ocrText", ocrText);
            response.put("structured", structured);
            if (humanDescription != null) {
                response.put("humanDescription", humanDescription);
            }
            log.info("📥 /analyze retourné ocrText ({} chars) et structured", ocrText != null ? ocrText.length() : 0);

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

            // ========== EXTRACTION EXPLICITE DES CHAMPS MANQUANTS ==========
            log.info("🔍 Vérification des champs manquants: {}", requestedFields);
            log.info("📌 Le champ 'nombre_nuits' est-il demandé? {}", requestedFields.contains("nombre_nuits"));
            log.info("📌 Le champ 'nombre_nuits' est-il déjà extrait? {}", extracted.containsKey("nombre_nuits"));

            // Extraction du nombre de nuits - FORCÉE
            if (requestedFields.contains("nombre_nuits")) {
                log.info("📌 FORCAGE: Extraction explicite du nombre de nuits");

                // Calcul direct depuis check_in/check_out
                String nights = null;
                Object checkInObj = structured.get("check_in");
                Object checkOutObj = structured.get("check_out");

                log.info("   - check_in: {}", checkInObj);
                log.info("   - check_out: {}", checkOutObj);

                if (checkInObj != null && checkOutObj != null) {
                    try {
                        String checkIn = checkInObj.toString();
                        String checkOut = checkOutObj.toString();

                        LocalDate start = parseDate(checkIn);
                        LocalDate end = parseDate(checkOut);

                        log.info("   - start parsée: {}", start);
                        log.info("   - end parsée: {}", end);

                        if (start != null && end != null) {
                            long nightsCalc = ChronoUnit.DAYS.between(start, end);
                            if (nightsCalc > 0) {
                                nights = String.valueOf(nightsCalc);
                                log.info("   ✅ Calcul direct: {} nuits", nights);
                            }
                        }
                    } catch (Exception e) {
                        log.error("   ❌ Erreur calcul direct: {}", e.getMessage());
                    }
                }

                // Si pas trouvé, essayer depuis la humanDescription
                if (nights == null && humanDescription != null) {
                    log.info("   - Tentative extraction depuis humanDescription");
                    String nightsFromDesc = extractNightsFromText(humanDescription);
                    if (nightsFromDesc != null) {
                        nights = nightsFromDesc;
                        log.info("   ✅ Trouvé dans humanDescription: {} nuits", nights);
                    }
                }

                // Si pas trouvé, essayer depuis l'OCR text
                if (nights == null && ocrText != null) {
                    log.info("   - Tentative extraction depuis OCR text");
                    Pattern pattern = Pattern.compile("(\\d+)\\s*(?:nuits?|nights?)", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(ocrText);
                    if (matcher.find()) {
                        nights = matcher.group(1);
                        log.info("   ✅ Trouvé dans OCR: {} nuits", nights);
                    }
                }

                if (nights != null && !nights.isEmpty()) {
                    extracted.put("nombre_nuits", nights);
                    log.info("✅ Nombre de nuits AJOUTÉ à extracted: {}", nights);
                } else {
                    log.warn("⚠️ Impossible d'extraire le nombre de nuits");
                    // Ajouter une valeur par défaut pour tester
                    extracted.put("nombre_nuits", "0");
                    log.info("📌 Valeur par défaut ajoutée: 0");
                }
            }

            // Extraction du nom d'hôtel
            if (requestedFields.contains("hotel_name") && !extracted.containsKey("hotel_name")) {
                log.info("📌 Extraction explicite du nom d'hôtel");
                Object hotelName = findExactKey(structured, "hotel_name");
                if (hotelName == null) hotelName = findExactKey(structured, "merchant_name");
                if (hotelName != null && !hotelName.toString().isEmpty()) {
                    extracted.put("hotel_name", hotelName.toString());
                    log.info("✅ Nom d'hôtel extrait: {}", hotelName);
                }
            }

            // Extraction du nombre de personnes
            if (requestedFields.contains("nombre_personnes") && !extracted.containsKey("nombre_personnes")) {
                log.info("📌 Extraction explicite du nombre de personnes");
                String guests = extractNumberOfGuests(structured);
                if (guests != null && !guests.isEmpty()) {
                    extracted.put("nombre_personnes", guests);
                    log.info("✅ Nombre de personnes extrait: {}", guests);
                }
            }

            // ========== FIN EXTRACTION EXPLICITE ==========

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

            log.info("✅ Champs finaux extraits: {}", extracted);

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

    // ========== EXTRACTION DES NUITS DEPUIS TEXTE ==========
    private String extractNightsFromText(String text) {
        if (text == null) return null;

        // Chercher "X nuits" dans le texte
        Pattern pattern = Pattern.compile("(\\d+)\\s*(?:nuits?|nights?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Chercher une plage de dates "du X au Y"
        Pattern datePattern = Pattern.compile(
                "du\\s+(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\s+au\\s+(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})",
                Pattern.CASE_INSENSITIVE
        );
        Matcher dateMatcher = datePattern.matcher(text);
        if (dateMatcher.find()) {
            try {
                LocalDate start = parseDate(dateMatcher.group(1));
                LocalDate end = parseDate(dateMatcher.group(2));
                if (start != null && end != null) {
                    long nights = ChronoUnit.DAYS.between(start, end);
                    if (nights > 0) {
                        return String.valueOf(nights);
                    }
                }
            } catch (Exception e) {
                log.debug("Erreur extraction nuits depuis texte: {}", e.getMessage());
            }
        }

        return null;
    }

    // ------------------------------------------------------------
    // extractFieldValueGeneric – avec support pour nuits, personnes, km, litres
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
        // description – use improved findBestDescription
        if (lowerField.equals("description") || lowerField.equals("objet") || lowerField.equals("motif")) {
            return findBestDescription(json);
        }
        // DEPART
        if (lowerField.equals("depart") || lowerField.equals("departure") || lowerField.equals("departure_location")) {
            Object origin = findExactKey(json, "origin");
            if (origin != null && !origin.toString().isBlank()) return origin;
            Object departurePlace = findValueByKeyContainingExceptTime(json, "departure");
            if (departurePlace != null && !departurePlace.toString().isBlank()) return departurePlace;
            Object val = findValueBySimilarKey(json, normalizeKey("origin"));
            if (val != null) return val;
            return null;
        }
        // DESTINATION
        if (lowerField.equals("destination") || lowerField.equals("arrival") || lowerField.equals("arrival_city")) {
            Object dest = findExactKey(json, "destination");
            if (dest != null) return dest;
            Object arrival = findValueBySimilarKey(json, normalizeKey("arrival"));
            if (arrival != null) return arrival;
            return null;
        }
        // TRANSPORT TYPE
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

        // ========== Gestion spécifique pour les quantités ==========

        // Nombre de nuits
        if (lowerField.equals("nuits") || lowerField.equals("nights") || lowerField.equals("nombre_nuits") ||
                lowerField.equals("number_of_nights") || lowerField.equals("nuit")) {
            return extractNumberOfNights(json);
        }

        // Nombre de personnes
        if (lowerField.equals("personnes") || lowerField.equals("guests") || lowerField.equals("nombre_personnes") ||
                lowerField.equals("number_of_guests") || lowerField.equals("pax") || lowerField.equals("voyageurs")) {
            return extractNumberOfGuests(json);
        }

        // Kilométrage
        if (lowerField.equals("km") || lowerField.equals("distance") || lowerField.equals("kilometres") ||
                lowerField.equals("mileage")) {
            return extractDistance(json);
        }

        // Quantité générale
        if (lowerField.equals("quantite") || lowerField.equals("quantity") || lowerField.equals("qty") ||
                lowerField.equals("nombre")) {
            return extractGeneralQuantity(json);
        }

        // Litres
        if (lowerField.equals("litres") || lowerField.equals("liters") || lowerField.equals("volume") ||
                lowerField.equals("fuel_quantity")) {
            return extractVolume(json);
        }

        // generic similar key for any other field
        String normalizedTarget = normalizeKey(lowerField);
        Object rawValue = findValueBySimilarKey(json, normalizedTarget);
        if (rawValue != null) return postProcessValue(rawValue, fieldName);
        return null;
    }

    // ========== METHODES D'EXTRACTION SPÉCIFIQUES ==========

    /**
     * Extrait le nombre de nuits d'un JSON d'hôtel
     */
    private String extractNumberOfNights(Map<String, Object> json) {
        log.info("🔍 Extraction nombre de nuits depuis JSON");

        // 1. Recherche directe des clés
        String[] nightKeys = {"nights", "number_of_nights", "nuit", "nuits", "nb_nuits", "quantity", "qty", "nbre_nuits"};
        for (String key : nightKeys) {
            Object value = findExactKey(json, key);
            if (value != null) {
                String extracted = extractNumberFromString(value.toString());
                if (extracted != null) {
                    try {
                        if (Double.parseDouble(extracted) > 0) {
                            log.info("✅ Nuits trouvées via clé '{}': {}", key, extracted);
                            return extracted;
                        }
                    } catch (NumberFormatException e) {}
                }
            }
        }

        // 2. Recherche par similarité
        Object nightValue = findValueByKeyContaining(json, "night");
        if (nightValue != null) {
            String extracted = extractNumberFromString(nightValue.toString());
            if (extracted != null) {
                try {
                    if (Double.parseDouble(extracted) > 0) {
                        log.info("✅ Nuits trouvées via similarité 'night': {}", extracted);
                        return extracted;
                    }
                } catch (NumberFormatException e) {}
            }
        }

        // 3. Calcul à partir des paires de dates
        Long nights = calculateNightsFromDatePairs(json);
        if (nights != null && nights > 0) {
            log.info("✅ Nuits calculées depuis dates: {}", nights);
            return String.valueOf(nights);
        }

        // 4. Recherche dans les items
        if (json.containsKey("items")) {
            Object items = json.get("items");
            if (items instanceof List) {
                for (Object item : (List<?>) items) {
                    if (item instanceof Map) {
                        Map<?, ?> itemMap = (Map<?, ?>) item;
                        Object name = itemMap.get("name");
                        if (name != null && name.toString().toLowerCase().contains("nuit")) {
                            String extracted = extractNumberFromString(name.toString());
                            if (extracted != null) {
                                log.info("✅ Nuits trouvées dans item name: {}", extracted);
                                return extracted;
                            }
                        }
                    }
                }
            }
        }

        log.warn("⚠️ Aucun nombre de nuits trouvé dans le JSON");
        return null;
    }

    /**
     * Calcule le nombre de nuits à partir de différentes paires de dates
     */
    private Long calculateNightsFromDatePairs(Map<String, Object> json) {
        String[][] datePairs = {
                {"check_in", "check_out"},
                {"checkin", "checkout"},
                {"date_depart", "date_arrivee"},
                {"departure_date", "arrival_date"},
                {"start_date", "end_date"},
                {"date_debut", "date_fin"},
                {"from_date", "to_date"},
                {"date1", "date2"},
                {"debut", "fin"},
                {"start", "end"},
                {"from", "to"}
        };

        for (String[] pair : datePairs) {
            Object startObj = findExactKey(json, pair[0]);
            Object endObj = findExactKey(json, pair[1]);

            if (startObj != null && endObj != null) {
                try {
                    LocalDate start = parseDate(startObj.toString());
                    LocalDate end = parseDate(endObj.toString());

                    if (start != null && end != null) {
                        long nights = ChronoUnit.DAYS.between(start, end);
                        if (nights > 0) {
                            log.debug("Calculé {} nuits entre {} et {}", nights, pair[0], pair[1]);
                            return nights;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Impossible de calculer les nuits avec {}/{}: {}", pair[0], pair[1], e.getMessage());
                }
            }
        }

        // Recherche récursive
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Long nested = calculateNightsFromDatePairs((Map<String, Object>) entry.getValue());
                if (nested != null && nested > 0) {
                    return nested;
                }
            }
        }

        return null;
    }

    /**
     * Extrait le nombre de personnes
     */
    private String extractNumberOfGuests(Map<String, Object> json) {
        String[] guestKeys = {"guests", "persons", "personnes", "pax", "number_of_guests", "nb_personnes",
                "adults", "adultes", "occupancy"};
        for (String key : guestKeys) {
            Object value = findExactKey(json, key);
            if (value != null) {
                String extracted = extractNumberFromString(value.toString());
                if (extracted != null) return extracted;
            }
        }

        Object guestValue = findValueByKeyContaining(json, "guest");
        if (guestValue != null) {
            String extracted = extractNumberFromString(guestValue.toString());
            if (extracted != null) return extracted;
        }

        return null;
    }

    /**
     * Extrait la distance / kilométrage
     */
    private String extractDistance(Map<String, Object> json) {
        String[] distanceKeys = {"km", "distance", "kilometres", "mileage", "kilometers", "kms"};
        for (String key : distanceKeys) {
            Object value = findExactKey(json, key);
            if (value != null) {
                String extracted = extractNumberFromString(value.toString());
                if (extracted != null) return extracted;
            }
        }

        Object distanceValue = findValueByKeyContaining(json, "km");
        if (distanceValue != null) {
            String extracted = extractNumberFromString(distanceValue.toString());
            if (extracted != null) return extracted;
        }

        return null;
    }

    /**
     * Extrait une quantité générale
     */
    private String extractGeneralQuantity(Map<String, Object> json) {
        String[] qtyKeys = {"quantity", "qty", "quantite", "nombre", "count"};
        for (String key : qtyKeys) {
            Object value = findExactKey(json, key);
            if (value != null) {
                String extracted = extractNumberFromString(value.toString());
                if (extracted != null) return extracted;
            }
        }
        return null;
    }

    /**
     * Extrait un volume (litres)
     */
    private String extractVolume(Map<String, Object> json) {
        String[] volumeKeys = {"litres", "liters", "volume", "fuel_quantity", "carburant"};
        for (String key : volumeKeys) {
            Object value = findExactKey(json, key);
            if (value != null) {
                String extracted = extractNumberFromString(value.toString());
                if (extracted != null) return extracted;
            }
        }
        return null;
    }

    /**
     * Extrait un nombre depuis une chaîne
     */
    private String extractNumberFromString(String text) {
        if (text == null) return null;
        Pattern pattern = Pattern.compile("\\d+([.,]\\d+)?");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String number = matcher.group();
            number = number.replace(',', '.');
            return number;
        }
        return null;
    }

    /**
     * Parse une date à partir de différents formats
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        dateStr = dateStr.trim();

        // Format français dd/MM/yyyy - AJOUTER CECI EN PREMIER
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return LocalDate.parse(dateStr, formatter);
        } catch (Exception e) {}

        // Format ISO yyyy-MM-dd
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {}

        // Autres formats
        String[] formats = {
                "MM/dd/yyyy", "dd-MM-yyyy", "MM-dd-yyyy",
                "yyyy/MM/dd", "dd.MM.yyyy", "MM.dd.yyyy"
        };
        for (String format : formats) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                return LocalDate.parse(dateStr, formatter);
            } catch (Exception ignored) {}
        }

        return null;
    }

    // Helper: find key containing target but exclude '_time'
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
    // IMPROVED findBestDescription
    // ------------------------------------------------------------
    private Object findBestDescription(Map<String, Object> json) {
        Object shortDesc = findShortDescription(json);
        if (shortDesc != null && !isTooLongOrLegal(shortDesc.toString())) {
            return shortDesc;
        }

        Object reason = findValueByKeyContaining(json, "reason");
        if (reason != null && !reason.toString().isBlank() && !isTooLongOrLegal(reason.toString())) {
            return reason.toString();
        }
        Object objet = findExactKey(json, "objet");
        if (objet != null && !objet.toString().isBlank() && !isTooLongOrLegal(objet.toString())) {
            return objet.toString();
        }

        Object docType = json.get("document_type");
        if (docType != null && !isGenericDocumentType(docType.toString())) {
            return docType.toString();
        }

        Object desc = findValueByKeyContaining(json, "description");
        if (desc != null && !desc.toString().isEmpty()) return desc;
        Object name = json.get("name");
        if (name != null && !name.toString().toLowerCase().contains("jones")) return name;

        return "receipt";
    }

    private Object findShortDescription(Map<String, Object> json) {
        Object paymentDesc = findValueByKeyContaining(json, "payment_description");
        if (paymentDesc != null && !paymentDesc.toString().isEmpty()) return paymentDesc;
        Object objet = findValueByKeyContaining(json, "objet");
        if (objet != null && !objet.toString().isEmpty()) return objet;
        Object title = findValueByKeyContaining(json, "title");
        if (title != null && !title.toString().isEmpty()) return title;
        Object reason = findValueByKeyContaining(json, "reason");
        if (reason != null && !reason.toString().isEmpty()) return reason;
        return null;
    }

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

    private boolean isGenericDocumentType(String docType) {
        String lower = docType.toLowerCase();
        return lower.equals("other") || lower.equals("receipt") || lower.equals("unknown") || lower.equals("document");
    }

    // ------------------------------------------------------------
    // Existing helper methods
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
                lowerField.contains("quantite") || lowerField.contains("litre") || lowerField.contains("volume") ||
                lowerField.equals("nights") || lowerField.equals("guests") || lowerField.equals("distance")) {
            String extracted = extractNumberFromString(str);
            if (extracted != null) {
                if (extracted.contains(".0") && !extracted.matches(".*\\.[1-9].*")) {
                    return extracted.replace(".0", "");
                }
                return extracted;
            }
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
        String[] priorityKeys = {"total_ttc", "total_tva_comprise", "grand_total", "total"};
        for (String key : priorityKeys) {
            Object exact = findExactKey(json, key);
            if (exact != null) {
                String norm = normalizeAmount(exact.toString());
                if (norm != null) return norm;
            }
        }

        Object best = null;
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String keyLower = entry.getKey().toLowerCase();
            if (keyLower.contains("total")) {
                if (keyLower.contains("sub") || keyLower.contains("sous") || keyLower.contains("ht")) {
                    if (best == null) best = entry.getValue();
                    continue;
                }
                String norm = normalizeAmount(entry.getValue().toString());
                if (norm != null) return norm;
            }
        }
        if (best != null) {
            String norm = normalizeAmount(best.toString());
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
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
                LocalDate date = LocalDate.parse(raw, formatter);
                return date.toString();
            } catch (Exception ignored) {}
        }
        return raw;
    }
}