// SessionContextUpdate.java
package com.coralio.chatbotmicroservice.dto;

import lombok.Data;
import java.util.Map;

@Data
public class SessionContextUpdate {
    private String currentPage;
    private String lastAction;
    private Map<String, Object> metadata;
}