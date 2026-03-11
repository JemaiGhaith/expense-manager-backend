package com.coralio.ai_microservice.services;

import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class FileHashService {

    public String computeMD5(byte[] fileBytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(fileBytes);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Erreur calcul MD5", e);
        }
    }
}