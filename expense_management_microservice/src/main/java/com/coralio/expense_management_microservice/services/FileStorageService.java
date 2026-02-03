package com.coralio.expense_management_microservice.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class FileStorageService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;
    private final Path uploadRoot = Paths.get("uploads");

    private Path fileStorageLocation;

    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (IOException ex) {
            throw new RuntimeException("Impossible de créer le dossier uploads", ex);
        }
    }

    // =========================
    // STOCKAGE FICHIER (PRO)
    // =========================
    public String storeFile(MultipartFile file, String employeeId) {
        try {
            // 1. Créer le dossier uploads/{employeeId}
            Path employeeDir = uploadRoot.resolve(String.valueOf(employeeId));
            Files.createDirectories(employeeDir);

            // 2. Nettoyer le nom du fichier
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());

            // 3. Séparer nom et extension
            String baseName = originalFilename;
            String extension = "";

            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = originalFilename.substring(0, dotIndex);
                extension = originalFilename.substring(dotIndex);
            }

            // 4. Générer un nom unique
            String finalFilename = originalFilename;
            int counter = 1;

            while (Files.exists(employeeDir.resolve(finalFilename))) {
                finalFilename = baseName + "(" + counter + ")" + extension;
                counter++;
            }

            // 5. Sauvegarder le fichier
            Path targetPath = employeeDir.resolve(finalFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return finalFilename;

        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'enregistrement du fichier", e);
        }
    }

    // =========================
    // AUTO-NUMÉROTATION
    // =========================
    private String resolveFileNameConflict(Path directory, String fileName) throws IOException {
        if (!Files.exists(directory.resolve(fileName))) {
            return fileName;
        }

        String name = fileName;
        String extension = "";

        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex != -1) {
            name = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        int counter = 1;
        String newFileName;

        do {
            newFileName = name + "_" + counter + extension;
            counter++;
        } while (Files.exists(directory.resolve(newFileName)));

        return newFileName;
    }

    // =========================
    // CHARGEMENT FICHIER
    // =========================
    public Resource loadFileAsResource(String employeeId, String fileName) {
        try {
            Path filePath = fileStorageLocation
                    .resolve(employeeId)
                    .resolve(fileName)
                    .normalize();

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                return resource;
            }

            throw new RuntimeException("Fichier introuvable");

        } catch (MalformedURLException ex) {
            throw new RuntimeException("Erreur chargement fichier", ex);
        }
    }
}
