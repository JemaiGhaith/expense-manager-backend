package com.coralio.ai_microservice.services;

import com.coralio.ai_microservice.model.DuplicateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.Map;
@Service
public class DuplicateDetectionService {
    @Autowired
    private OCRService ocr;
    public DuplicateResult checkDuplicate(MultipartFile file) throws Exception {

        /*Path temp = Files.createTempFile("upload",".tmp");

        Files.copy(file.getInputStream(), temp, StandardCopyOption.REPLACE_EXISTING);

        String text = ocr.extractText(temp.toFile());
*/
        String text = ocr.extractText(file);
        RestTemplate rest = new RestTemplate();

        Map<String,String> body = Map.of(
                "text", text,
                "filename", file.getOriginalFilename()
        );

        Map response = rest.postForObject(
                "http://localhost:9000/check-duplicate",
                body,
                Map.class
        );

        boolean duplicate = (boolean) response.get("duplicate");

        double score = Double.parseDouble(response.get("score").toString());

        String existingFile = (String) response.get("file");

        return new DuplicateResult(duplicate, existingFile, score);
    }
}