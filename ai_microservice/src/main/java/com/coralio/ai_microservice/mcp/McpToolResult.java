package com.coralio.ai_microservice.mcp;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class McpToolResult {
    private boolean success;
    private String content;     // Résultat texte ou base64
    private String error;
    private boolean isBase64;   // true si c'est une image
}