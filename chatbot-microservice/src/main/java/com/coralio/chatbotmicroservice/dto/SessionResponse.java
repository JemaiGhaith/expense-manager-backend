// SessionResponse.java
package com.coralio.chatbotmicroservice.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class SessionResponse {
    private String sessionToken;
    private String userId;
    private String userRole;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;
}