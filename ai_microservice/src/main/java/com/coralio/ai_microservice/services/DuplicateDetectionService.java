package com.coralio.ai_microservice.services;

import com.coralio.ai_microservice.model.DuplicateResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DuplicateDetectionService {

    @Autowired
    private OCRService ocr;

    @Value("${uploads.dir:C:\\Users\\MSI\\coralio_backend\\uploads}")
    private String uploadsDir;

    public DuplicateResult checkDuplicate(MultipartFile file, String employeeId) throws Exception {

        String text = ocr.extractText(file);
        String filename = file.getOriginalFilename();

        // ✅ Construire le chemin absolu
        String fullPath = buildFullPath(employeeId, filename);
        System.out.println("📁 [AI] Chemin absolu: " + fullPath);

        RestTemplate rest = new RestTemplate();

        // ✅ HashMap pour accepter valeurs mixtes
        Map<String, String> body = new HashMap<>();
        body.put("text", text);
        body.put("filename", filename);
        body.put("filepath", fullPath);

        Map response = rest.postForObject(
                "http://localhost:9000/check-duplicate",
                body,
                Map.class
        );

        boolean duplicate = (boolean) response.get("duplicate");
        double score = Double.parseDouble(response.get("score").toString());

        // ✅ "file" = nom fichier, "path" = chemin absolu
        String existingFile = response.get("file") != null
                ? response.get("file").toString() : null;

        String existingPath = response.get("path") != null
                ? response.get("path").toString() : existingFile;

        System.out.println("✅ [AI] duplicate=" + duplicate
                + " | score=" + score
                + " | file=" + existingFile
                + " | path=" + existingPath);

        return new DuplicateResult(duplicate, existingFile, existingPath, score);
    }

    private String buildFullPath(String employeeId, String filename) {
        if (employeeId == null || employeeId.isBlank()) {
            return Paths.get(uploadsDir, filename).toAbsolutePath().toString();
        }

        String[] subFolders = {"factures", "accords"};
        for (String sub : subFolders) {
            Path path = Paths.get(uploadsDir, employeeId, sub, filename);
            if (Files.exists(path)) {
                System.out.println("📁 [AI] Fichier trouvé dans: " + sub);
                return path.toAbsolutePath().toString();
            }
        }

        // Fichier pas encore sauvegardé → chemin attendu par défaut
        return Paths.get(uploadsDir, employeeId, "factures", filename)
                .toAbsolutePath().toString();
    }



    // ✅ Ajouter cette méthode dans la classe
    public Map<String, Object> extractFields(MultipartFile file, String fieldsJson) throws Exception {
        String text = ocr.extractText(file);
        String filename = file.getOriginalFilename();

        System.out.println("📄 [EXTRACT] Fichier: " + filename);
        System.out.println("📄 [EXTRACT] Texte extrait (" + text.length() + " chars): "
                + text.substring(0, Math.min(200, text.length())));

        // Parser les champs demandés
        List<String> fields = List.of();
        if (fieldsJson != null && !fieldsJson.isBlank()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                fields = mapper.readValue(fieldsJson,
                        mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (Exception e) {
                System.err.println("⚠️ Erreur parsing fields JSON: " + e.getMessage());
            }
        }

        RestTemplate rest = new RestTemplate();

        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("fields", fields);

        System.out.println("📤 [EXTRACT] Envoi au service Python avec champs: " + fields);

        try {
            Map response = rest.postForObject(
                    "http://localhost:9000/extract-fields",
                    body,
                    Map.class
            );

            System.out.println("✅ [EXTRACT] Réponse Python: " + response);
            return response != null ? response : Map.of("fields", Map.of());

        } catch (Exception e) {
            System.err.println("❌ [EXTRACT] Erreur service Python: " + e.getMessage());
            return Map.of("fields", Map.of(), "error", e.getMessage());
        }
    }




}