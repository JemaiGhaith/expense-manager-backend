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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;
    private final Path uploadRoot = Paths.get("uploads");

    private Path fileStorageLocation;

    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
            log.info("Dossier uploads initialisé: {}", this.fileStorageLocation);
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

            log.info("Fichier sauvegardé: {}", targetPath);

            // 6. Retourner le chemin relatif (incluant le type)
            return type + "/" + finalFilename;

        } catch (IOException e) {
            log.error("Erreur lors de l'enregistrement du fichier", e);
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

            log.debug("Chargement fichier: {}", fullPath);

            Resource resource = new UrlResource(fullPath.toUri());

            if (resource.exists()) {
                return resource;
            }

            throw new RuntimeException("Fichier introuvable: " + filePath);

        } catch (MalformedURLException ex) {
            log.error("Erreur chargement fichier", ex);
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

            log.info("Fichier supprimé: {}", fullPath);

        } catch (IOException e) {
            log.error("Erreur lors de la suppression du fichier", e);
            throw new RuntimeException("Erreur lors de la suppression du fichier: " + filePath, e);
        }
    }

    // =========================
    // MÉTHODES AJOUTÉES POUR LES PDF
    // =========================

    /**
     * Sauvegarde un PDF dans le dossier de l'employé
     */
    public void storePdf(byte[] content, String employeeId, String type, String filename) {
        try {
            Path employeeDir = getOrCreateEmployeeDirectory(employeeId);
            Path typeDir = employeeDir.resolve(type);

            if (!Files.exists(typeDir)) {
                Files.createDirectories(typeDir);
                log.info("Dossier créé: {}", typeDir);
            }

            Path filePath = typeDir.resolve(filename);
            Files.write(filePath, content);

            log.info("PDF sauvegardé: {}", filePath);

        } catch (IOException e) {
            log.error("Erreur lors de la sauvegarde du PDF: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la sauvegarde du PDF", e);
        }
    }

    /**
     * Récupère un PDF
     */
    public Resource loadPdfAsResource(String employeeId, String type, String filename) {
        try {
            Path employeeDir = getOrCreateEmployeeDirectory(employeeId);
            Path filePath = employeeDir.resolve(type).resolve(filename);

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Fichier non trouvé: " + filename);
            }
        } catch (MalformedURLException e) {
            log.error("Erreur lors de la lecture du fichier", e);
            throw new RuntimeException("Erreur lors de la lecture du fichier: " + filename, e);
        } catch (IOException e) {
            log.error("Erreur lors de l'accès au dossier employé", e);
            throw new RuntimeException("Erreur lors de l'accès au dossier employé: " + employeeId, e);
        }
    }

    /**
     * Méthode utilitaire pour créer/récupérer le dossier d'un employé
     * Gère l'exception IOException
     */
    private Path getOrCreateEmployeeDirectory(String employeeId) {
        try {
            Path employeeDir = uploadRoot.resolve(String.valueOf(employeeId));
            if (!Files.exists(employeeDir)) {
                Files.createDirectories(employeeDir);
                log.info("Dossier employé créé: {}", employeeDir);
            }
            return employeeDir;
        } catch (IOException e) {
            log.error("Erreur lors de la création du dossier employé: {}", employeeId, e);
            throw new RuntimeException("Erreur lors de la création du dossier pour l'employé: " + employeeId, e);
        }
    }


    public MultipartFile getFileAsMultipart(String employeeId, String filename) {
        try {
            Path path = Paths.get("uploads/" + employeeId + "/" + filename);
            byte[] content = Files.readAllBytes(path);

            return new MockMultipartFile(
                    filename,
                    filename,
                    Files.probeContentType(path),
                    content
            );
        } catch (Exception e) {
            throw new RuntimeException("Erreur récupération fichier");
        }
    }


}