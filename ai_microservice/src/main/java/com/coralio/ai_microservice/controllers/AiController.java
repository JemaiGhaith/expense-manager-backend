package com.coralio.ai_microservice.controllers;


import com.coralio.ai_microservice.model.DuplicateResult;
import com.coralio.ai_microservice.services.DuplicateDetectionService;
import com.coralio.ai_microservice.services.PythonAnalyzeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    DuplicateDetectionService service;
    @Autowired
    private PythonAnalyzeService pythonAnalyzeService;

    @PostMapping("/check-duplicate")
    public DuplicateResult check(
            @RequestParam MultipartFile file,
            @RequestParam(value = "employeeId", required = false) String employeeId
    ) throws Exception {
        return service.checkDuplicate(file, employeeId);  // ✅ passer employeeId
    }


    // ✅ Ajouter cette méthode dans la classe AiController
    @PostMapping("/extract-fields")
    public ResponseEntity<Map<String, Object>> extractFields(
            @RequestParam MultipartFile file,
            @RequestParam(value = "fields", required = false) String fieldsJson
    ) throws Exception {
        return ResponseEntity.ok(service.extractFields(file, fieldsJson));
    }

    @PostMapping("/analyze-accord")
    public ResponseEntity<?> analyzeAccord(@RequestParam MultipartFile file) {
        try {
            Object result = pythonAnalyzeService.callPythonAnalyze(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/compare-accord-form")
    public ResponseEntity<?> compareAccordForm(
            @RequestParam("file") MultipartFile file,
            @RequestParam("formData") String formDataJson
    ) {
        try {
            // Appel au service Python
            String pythonUrl = "http://localhost:8000/analyze-full";

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("form_data", formDataJson);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.postForObject(pythonUrl, requestEntity, String.class);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================================
    // ✅ NOUVEL ENDPOINT FUSIONNÉ : /analyze-full
    // ============================================================

    @PostMapping("/analyze-full")
    public ResponseEntity<?> analyzeFull(
            @RequestParam("file") MultipartFile file,
            @RequestParam("form_data") String formDataJson
    ) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String pythonUrl = "http://localhost:8000/analyze-full";

            // --------------------------------------------------------
            // ÉTAPE 1 : Envoyer le fichier au service Python
            // --------------------------------------------------------
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("form_data", formDataJson);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            // Appel au service Python
            String pythonResponse = restTemplate.postForObject(
                    pythonUrl,
                    requestEntity,
                    String.class
            );

            // --------------------------------------------------------
            // ÉTAPE 2 : Parser et structurer la réponse
            // --------------------------------------------------------
            JsonNode pythonResult = objectMapper.readTree(pythonResponse);

            // Construire la réponse finale
            ObjectNode finalResponse = objectMapper.createObjectNode();

            // Section 1 : Analyse de l'accord (inchangée)
            finalResponse.set("accord_analysis", pythonResult.get("accord_analysis"));

            // Section 2 : Comparaison (inchangée)
            finalResponse.set("comparison", pythonResult.get("comparison"));

            return ResponseEntity.ok(finalResponse);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors de l'analyse fusionnée: " + e.getMessage()));
        }
    }
}