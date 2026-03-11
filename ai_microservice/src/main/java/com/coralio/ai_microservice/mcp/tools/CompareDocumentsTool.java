package com.coralio.ai_microservice.mcp.tools;


import com.coralio.ai_microservice.mcp.McpTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import com.coralio.ai_microservice.mcp.McpToolResult;

@Component
public class CompareDocumentsTool implements McpTool {

    @Override
    public String getName() {
        return "report_duplicate_result";
    }

    @Override
    public String getDescription() {
        return """
            Utilise cet outil pour rapporter ton résultat final d'analyse de doublon.
            Après avoir analysé visuellement les documents avec read_file_as_image,
            utilise cet outil pour soumettre ta conclusion.
            Paramètres:
            - is_duplicate: "true" ou "false"
            - confidence: valeur entre 0.0 et 1.0
            - matched_file: chemin du fichier doublon trouvé (ou "none")
            - reason: explication en français de ta décision
            """;
    }

    @Override
    public String getParametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "is_duplicate": {
                  "type": "string",
                  "description": "true ou false"
                },
                "confidence": {
                  "type": "string",
                  "description": "Score de confiance entre 0.0 et 1.0"
                },
                "matched_file": {
                  "type": "string",
                  "description": "Chemin relatif du fichier doublon ou none"
                },
                "reason": {
                  "type": "string",
                  "description": "Explication de la décision en français"
                }
              },
              "required": ["is_duplicate", "confidence", "matched_file", "reason"]
            }
            """;
    }

    @Override
    public McpToolResult execute(Map<String, String> parameters) {
        // Cet outil est juste un "rapporteur" — son résultat est capturé
        // par le McpServer pour construire la DuplicateCheckResponse finale
        String isDuplicate  = parameters.getOrDefault("is_duplicate", "false");
        String confidence   = parameters.getOrDefault("confidence", "0.0");
        String matchedFile  = parameters.getOrDefault("matched_file", "none");
        String reason       = parameters.getOrDefault("reason", "");

        System.out.println("🎯 [MCP Result] isDuplicate=" + isDuplicate
                + ", confidence=" + confidence
                + ", matchedFile=" + matchedFile
                + ", reason=" + reason);

        return McpToolResult.builder()
                .success(true)
                .content("FINAL_RESULT|" + isDuplicate + "|" + confidence
                        + "|" + matchedFile + "|" + reason)
                .build();
    }
}