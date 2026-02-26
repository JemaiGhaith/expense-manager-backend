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
    // STOCKAGE FICHIER (AVEC TYPE)
    // =========================
    public String storeFile(MultipartFile file, String employeeId, String type) {
        try {
            // 1. Créer le dossier uploads/{employeeId}/{type}
            Path employeeDir = uploadRoot.resolve(String.valueOf(employeeId)).resolve(type);
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

            // 6. Retourner le chemin relatif (incluant le type)
            return type + "/" + finalFilename;

        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'enregistrement du fichier", e);
        }
    }

    // Méthode pour compatibilité avec l'existant (par défaut dossier "factures")
    public String storeFile(MultipartFile file, String employeeId) {
        return storeFile(file, employeeId, "factures");
    }

    // =========================
    // CHARGEMENT FICHIER
    // =========================
    public Resource loadFileAsResource(String employeeId, String filePath) {
        try {
            // Le filePath contient déjà le type (ex: "accords/monfichier.pdf")
            Path fullPath = uploadRoot
                    .resolve(String.valueOf(employeeId))
                    .resolve(filePath)
                    .normalize();

            Resource resource = new UrlResource(fullPath.toUri());

            if (resource.exists()) {
                return resource;
            }

            throw new RuntimeException("Fichier introuvable: " + filePath);

        } catch (MalformedURLException ex) {
            throw new RuntimeException("Erreur chargement fichier", ex);
        }
    }

    // =========================
    // SUPPRESSION FICHIER
    // =========================
    /**
     * Supprime un fichier du disque.
     * @param employeeId Identifiant de l'employé
     * @param filePath Chemin relatif du fichier (doit commencer par "accords/" ou "factures/")
     * @throws RuntimeException si le fichier n'existe pas ou en cas d'erreur
     */
    public void deleteFile(String employeeId, String filePath) {
        try {
            // Construire le chemin complet
            Path fullPath = uploadRoot
                    .resolve(String.valueOf(employeeId))
                    .resolve(filePath)
                    .normalize();

            // Sécurité : vérifier que le chemin résolu est bien dans le répertoire de base
            if (!fullPath.startsWith(uploadRoot.resolve(employeeId))) {
                throw new RuntimeException("Tentative de suppression en dehors du répertoire autorisé");
            }

            boolean deleted = Files.deleteIfExists(fullPath);
            if (!deleted) {
                throw new RuntimeException("Fichier introuvable: " + filePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la suppression du fichier: " + filePath, e);
        }
    }
}