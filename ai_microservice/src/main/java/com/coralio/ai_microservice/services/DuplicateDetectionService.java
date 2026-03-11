package com.coralio.ai_microservice.services;

import com.coralio.ai_microservice.dto.DuplicateCheckResponse;
import com.coralio.ai_microservice.mcp.McpServer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

@Service
public class DuplicateDetectionService {

    private final McpServer mcpServer;

    public DuplicateDetectionService(McpServer mcpServer) {
        this.mcpServer = mcpServer;
    }

    public DuplicateCheckResponse checkDuplicate(
            byte[] fileBytes,
            String filename,
            String contentType
    ) {
        System.out.println("🔍 [AI] checkDuplicate appelé pour: " + filename);
        System.out.println("🔍 [AI] Taille fichier: " + fileBytes.length + " bytes");

        try {
            // Valider que le fichier n'est pas vide
            if (fileBytes == null || fileBytes.length == 0) {
                return DuplicateCheckResponse.builder()
                        .isDuplicate(false)
                        .confidence(0.0)
                        .reason("Fichier vide")
                        .detectionMethod("NONE")
                        .documentType("Document")
                        .build();
            }

            // Convertir en image de façon robuste
            String base64Image = convertToBase64Image(fileBytes, filename);

            if (base64Image == null || base64Image.isEmpty()) {
                System.out.println("⚠️ [AI] Impossible de convertir en image: " + filename);
                return DuplicateCheckResponse.builder()
                        .isDuplicate(false)
                        .confidence(0.0)
                        .reason("Format de fichier non analysable")
                        .detectionMethod("NONE")
                        .documentType(detectTypeFromExtension(filename))
                        .build();
            }

            System.out.println("✅ [AI] Image convertie en base64 (" + base64Image.length() + " chars)");

            // Lancer la détection MCP
            DuplicateCheckResponse response = mcpServer.runDuplicateDetection(base64Image, filename);

            // Log du résultat
            System.out.println("📊 [AI] Résultat: " + (response.isDuplicate() ? "DOUBLON" : "NON DOUBLON")
                    + " (confiance: " + response.getConfidence() + ")");

            return response;

        } catch (Exception e) {
            System.err.println("❌ [AI] Erreur détection: " + e.getMessage());
            e.printStackTrace();
            return DuplicateCheckResponse.builder()
                    .isDuplicate(false)
                    .confidence(0.0)
                    .reason("Erreur analyse: " + e.getMessage())
                    .detectionMethod("ERROR")
                    .documentType(detectTypeFromExtension(filename))
                    .build();
        }
    }

    private String convertToBase64Image(byte[] fileBytes, String filename) {
        try {
            String lower = filename.toLowerCase();
            BufferedImage image = null;

            // PDF
            if (lower.endsWith(".pdf")) {
                try (var pdf = Loader.loadPDF(fileBytes)) {
                    PDFRenderer renderer = new PDFRenderer(pdf);
                    image = renderer.renderImageWithDPI(0, 150);
                }
            }
            // Images
            else {
                image = ImageIO.read(new ByteArrayInputStream(fileBytes));
            }

            if (image == null) {
                // Créer une image placeholder avec le nom du fichier
                image = createPlaceholderImage(filename);
            }

            // Forcer le format RGB et redimensionner
            image = ensureRGBAndResize(image, 800, 800);

            // Convertir en base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (Exception e) {
            System.err.println("❌ [CONVERT] Exception: " + e.getMessage());
            // Dernier recours: créer un placeholder
            try {
                BufferedImage placeholder = createPlaceholderImage(filename);
                placeholder = ensureRGBAndResize(placeholder, 800, 800);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(placeholder, "png", baos);
                return Base64.getEncoder().encodeToString(baos.toByteArray());
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private BufferedImage ensureRGBAndResize(BufferedImage src, int maxW, int maxH) {
        // Créer une nouvelle image RGB si nécessaire
        BufferedImage rgbImage;
        if (src.getType() != BufferedImage.TYPE_INT_RGB) {
            rgbImage = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgbImage.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, src.getWidth(), src.getHeight());
            g.drawImage(src, 0, 0, null);
            g.dispose();
        } else {
            rgbImage = src;
        }

        // Redimensionner si nécessaire
        int w = rgbImage.getWidth();
        int h = rgbImage.getHeight();

        if (w <= maxW && h <= maxH) {
            return rgbImage;
        }

        double ratio = Math.min((double) maxW / w, (double) maxH / h);
        int newW = (int) (w * ratio);
        int newH = (int) (h * ratio);

        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(rgbImage, 0, 0, newW, newH, null);
        g.dispose();

        return resized;
    }

    private BufferedImage createPlaceholderImage(String filename) {
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Fond blanc
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 300);

        // Texte
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font("Arial", Font.BOLD, 16));

        // Nom du fichier (tronqué si trop long)
        String displayName = filename.length() > 30 ? filename.substring(0, 27) + "..." : filename;
        g.drawString("📄 " + displayName, 20, 130);

        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.drawString("Document à analyser", 20, 160);
        g.drawString("Format: " + detectTypeFromExtension(filename), 20, 180);

        g.dispose();
        return img;
    }

    private String detectTypeFromExtension(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".png")) return "PNG";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "JPEG";
        if (lower.endsWith(".gif")) return "GIF";
        if (lower.endsWith(".bmp")) return "BMP";
        if (lower.endsWith(".webp")) return "WebP";
        return "Document";
    }
}