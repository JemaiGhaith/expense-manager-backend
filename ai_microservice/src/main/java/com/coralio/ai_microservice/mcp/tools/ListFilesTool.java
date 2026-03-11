package com.coralio.ai_microservice.mcp.tools;

import com.coralio.ai_microservice.mcp.McpTool;
import com.coralio.ai_microservice.mcp.McpToolResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ListFilesTool implements McpTool {

    @Value("${uploads.dir}")
    private String uploadsDir;

    @Override
    public String getName() {
        return "list_existing_files";
    }

    @Override
    public String getDescription() {
        return """
            Liste TOUS les fichiers justificatifs existants dans le système, 
            dans TOUS les sous-dossiers sans exception.
            Utilise cet outil en premier pour savoir quels fichiers sont disponibles.
            Retourne une liste de chemins relatifs de tous les fichiers existants.
            """;
    }

    @Override
    public String getParametersSchema() {
        return """
            {
              "type": "object",
              "properties": {},
              "required": []
            }
            """;
    }

    @Override
    public McpToolResult execute(Map<String, String> params) {
        try {
            Path rootPath = Paths.get(uploadsDir);
            File rootDir = rootPath.toFile();

            if (!rootDir.exists() || !rootDir.isDirectory()) {
                return McpToolResult.builder()
                        .success(false)
                        .content("Dossier uploads introuvable: " + uploadsDir)
                        .build();
            }

            // ✅ Scan récursif de TOUS les sous-dossiers sans exception
            List<String> allFiles = new ArrayList<>();
            scanAllDirectories(rootPath, rootPath, allFiles);

            System.out.println("📂 [MCP ListFiles] " + allFiles.size() + " fichiers trouvés (TOUS les sous-dossiers)");

            String content = String.join("\n", allFiles);
            return McpToolResult.builder()
                    .success(true)
                    .content(content)
                    .build();

        } catch (Exception e) {
            return McpToolResult.builder()
                    .success(false)
                    .content("Erreur: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Parcourt récursivement TOUS les sous-dossiers sans exception
     */
    private void scanAllDirectories(Path rootPath, Path currentPath, List<String> result) {
        File currentDir = currentPath.toFile();
        File[] files = currentDir.listFiles();

        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // ✅ Récursion dans TOUS les sous-dossiers, quel que soit leur nom
                scanAllDirectories(rootPath, file.toPath(), result);
            } else {
                String name = file.getName().toLowerCase();
                // ✅ Accepter tous les formats d'image et PDF
                if (name.endsWith(".pdf") || name.endsWith(".png")
                        || name.endsWith(".jpg") || name.endsWith(".jpeg")
                        || name.endsWith(".gif") || name.endsWith(".bmp")
                        || name.endsWith(".webp")) {

                    // ✅ Chemin relatif par rapport au dossier uploads
                    String relativePath = rootPath.relativize(file.toPath())
                            .toString()
                            .replace("/", "\\");

                    result.add(relativePath);
                }
            }
        }
    }
}