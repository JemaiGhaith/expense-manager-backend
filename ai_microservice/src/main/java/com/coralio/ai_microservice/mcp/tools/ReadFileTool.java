package com.coralio.ai_microservice.mcp.tools;

import com.coralio.ai_microservice.mcp.McpTool;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.coralio.ai_microservice.mcp.McpToolResult;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

@Component
public class ReadFileTool implements McpTool {

    @Value("${uploads.dir}")
    private String uploadsDir;

    @Override
    public String getName() {
        return "read_file_as_image";
    }

    @Override
    public String getDescription() {
        return """
            Lit un fichier justificatif et le retourne en base64 pour analyse visuelle.
            Supporte PDF (convertit la première page), JPEG, PNG.
            Utilise ce tool pour lire le fichier nouvellement uploadé ET
            chaque fichier existant que tu veux comparer.
            Paramètre: file_path = chemin relatif du fichier depuis le dossier uploads
            """;
    }

    @Override
    public String getParametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "file_path": {
                  "type": "string",
                  "description": "Chemin relatif du fichier depuis uploads/"
                }
              },
              "required": ["file_path"]
            }
            """;
    }

    @Override
    public McpToolResult execute(Map<String, String> parameters) {
        String filePath = parameters.get("file_path");

        if (filePath == null || filePath.isBlank()) {
            return McpToolResult.builder()
                    .success(false)
                    .error("Paramètre file_path manquant")
                    .build();
        }

        try {
            Path fullPath = Paths.get(uploadsDir).resolve(filePath).normalize();

            if (!Files.exists(fullPath)) {
                return McpToolResult.builder()
                        .success(false)
                        .error("Fichier introuvable: " + filePath)
                        .build();
            }

            byte[] fileBytes = Files.readAllBytes(fullPath);
            String filename  = fullPath.getFileName().toString().toLowerCase();

            BufferedImage image = null;

            // ── PDF → image ─────────────────────────────────────
            if (filename.endsWith(".pdf")) {
                try (PDDocument pdf = org.apache.pdfbox.Loader.loadPDF(fileBytes)) {
                    PDFRenderer renderer = new PDFRenderer(pdf);
                    image = renderer.renderImageWithDPI(0, 150);
                }
            }
            // ── Image directe ───────────────────────────────────
            else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")
                    || filename.endsWith(".png")) {
                image = ImageIO.read(new ByteArrayInputStream(fileBytes));
            }

            if (image == null) {
                return McpToolResult.builder()
                        .success(false)
                        .error("Impossible de lire le fichier comme image: " + filePath)
                        .build();
            }

            // Redimensionner pour optimiser l'envoi à Ollama
            image = resizeImage(image, 800, 800);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            System.out.println("📄 [MCP ReadFile] Lu: " + filePath
                    + " (" + fileBytes.length / 1024 + " KB)");

            return McpToolResult.builder()
                    .success(true)
                    .content(base64)
                    .isBase64(true)
                    .build();

        } catch (Exception e) {
            return McpToolResult.builder()
                    .success(false)
                    .error("Erreur lecture fichier " + filePath + ": " + e.getMessage())
                    .build();
        }
    }

    private BufferedImage resizeImage(BufferedImage src, int maxW, int maxH) {
        int w = src.getWidth();
        int h = src.getHeight();

        if (w <= maxW && h <= maxH) return src;

        double ratio = Math.min((double) maxW / w, (double) maxH / h);
        int newW = (int) (w * ratio);
        int newH = (int) (h * ratio);

        BufferedImage result = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();
        return result;
    }
}