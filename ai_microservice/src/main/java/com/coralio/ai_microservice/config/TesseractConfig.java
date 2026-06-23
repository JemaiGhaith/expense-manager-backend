package com.coralio.ai_microservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TesseractConfig {

    @Value("${tesseract.datapath}")
    private String dataPath;

    @Value("${tesseract.language}")
    private String language;

    // Le bean Tesseract est déjà créé dans OcrService via constructeur.
    // Cette classe peut rester vide ou être utilisée pour d'autres configs.
}