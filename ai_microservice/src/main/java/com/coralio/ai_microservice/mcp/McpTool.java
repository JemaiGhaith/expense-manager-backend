package com.coralio.ai_microservice.mcp;


import java.util.Map;

public interface McpTool {

    // Nom de l'outil exposé à Ollama
    String getName();

    // Description que lit Ollama pour savoir quand utiliser l'outil
    String getDescription();

    // Schéma des paramètres (format JSON Schema simplifié)
    String getParametersSchema();

    // Exécution réelle de l'outil
    McpToolResult execute(Map<String, String> parameters);
}