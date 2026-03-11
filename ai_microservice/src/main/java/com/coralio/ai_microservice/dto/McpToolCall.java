package com.coralio.ai_microservice.dto;

import lombok.Data;
import java.util.Map;

@Data
public class McpToolCall {
    private String toolName;
    private Map<String, String> parameters;
}