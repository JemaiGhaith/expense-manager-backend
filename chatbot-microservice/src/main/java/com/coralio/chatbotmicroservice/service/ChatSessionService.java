// ChatSessionService.java
package com.coralio.chatbotmicroservice.service;

import com.coralio.chatbotmicroservice.entity.ChatMessage;
import com.coralio.chatbotmicroservice.entity.ChatSession;
import com.coralio.chatbotmicroservice.entity.SessionStatus;
import com.coralio.chatbotmicroservice.repository.ChatMessageRepository;
import com.coralio.chatbotmicroservice.repository.ChatSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.hibernate.Hibernate;
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    @Value("${chatbot.session.timeout-minutes:30}")
    private int sessionTimeoutMinutes;

    // In-memory cache for active sessions
    private final Map<String, CachedSession> sessionCache = new ConcurrentHashMap<>();

    @Transactional
    public ChatSession createSession(String userId, String userRole) {
        // Close any existing active sessions for this user
        List<ChatSession> activeSessions = sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE);
        activeSessions.forEach(session -> {
            session.setStatus(SessionStatus.CLOSED);
            sessionRepository.save(session);
            sessionCache.remove(session.getSessionToken());
            log.info("Closed existing session {} for user {}", session.getSessionToken(), userId);
        });

        // Create new session
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .userRole(userRole)
                .status(SessionStatus.ACTIVE)
                .lastActivity(LocalDateTime.now())
                .context(createInitialContext())
                .build();

        ChatSession saved = sessionRepository.save(session);
        cacheSession(saved);

        log.info("✅ Created new session: {} for user: {}", saved.getSessionToken(), userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<ChatSession> getSession(String sessionToken) {
        // Check cache first
        CachedSession cached = sessionCache.get(sessionToken);
        if (cached != null && !isExpired(cached)) {
            log.debug("Session {} found in cache", sessionToken);
            return Optional.of(cached.session);
        }

        // Cache miss or expired, get from DB
        Optional<ChatSession> sessionOpt = sessionRepository.findBySessionTokenAndStatus(sessionToken, SessionStatus.ACTIVE);

        sessionOpt.ifPresent(session -> {
            if (!isExpired(session)) {
                cacheSession(session);
            } else {
                expireSession(session);
            }
        });

        return sessionOpt;
    }

    @Transactional
    public void updateSessionContext(String sessionToken, Map<String, Object> contextUpdate) {
        getSession(sessionToken).ifPresent(session -> {
            try {
                ObjectNode context = (ObjectNode) objectMapper.readTree(session.getContext());
                contextUpdate.forEach((key, value) -> context.putPOJO(key, value));
                session.setContext(objectMapper.writeValueAsString(context));
                session.setLastActivity(LocalDateTime.now());
                sessionRepository.save(session);
                cacheSession(session);
                log.debug("Updated context for session {}", sessionToken);
            } catch (Exception e) {
                log.error("Error updating session context", e);
            }
        });
    }

    @Transactional
    public void addMessage(String sessionToken, String messageText, boolean isUser,
                           String intent, Double confidence, Integer processingTimeMs, String quickReplies) {
        getSession(sessionToken).ifPresent(session -> {
            ChatMessage message = ChatMessage.builder()
                    .session(session)
                    .messageText(messageText)
                    .isUser(isUser)
                    .intent(intent)
                    .confidence(confidence)
                    .processingTimeMs(processingTimeMs)
                    .quickReplies(quickReplies)
                    .build();

            messageRepository.save(message);

            if (isUser) {
                session.setLastQuestion(messageText);
            }
            session.setLastActivity(LocalDateTime.now());
            sessionRepository.save(session);
            cacheSession(session);

            log.debug("Added {} message to session {}", isUser ? "user" : "bot", sessionToken);
        });
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getSessionHistory(String sessionToken, int limit) {
        return getSession(sessionToken)
                .map(session -> messageRepository.findBySessionOrderByCreatedAtDesc(session, limit))
                .orElse(Collections.emptyList());
    }

    @Transactional
    public void extendSession(String sessionToken) {
        getSession(sessionToken).ifPresent(session -> {
            session.setLastActivity(LocalDateTime.now());
            sessionRepository.save(session);
            cacheSession(session);
            log.debug("Extended session {}", sessionToken);
        });
    }

    @Transactional
    public void closeSession(String sessionToken) {
        getSession(sessionToken).ifPresent(session -> {
            session.setStatus(SessionStatus.CLOSED);
            sessionRepository.save(session);
            sessionCache.remove(sessionToken);
            log.info("Closed session {}", sessionToken);
        });
    }

    public boolean isValidSession(String sessionToken) {
        return getSession(sessionToken)
                .map(session -> !isExpired(session))
                .orElse(false);
    }

    @Scheduled(fixedDelay = 600000) // Run every 10 minutes
    @Transactional
    public void cleanupExpiredSessions() {
        LocalDateTime expiryThreshold = LocalDateTime.now().minusMinutes(sessionTimeoutMinutes);

        List<ChatSession> expiredSessions = sessionRepository.findByStatusAndLastActivityBefore(
                SessionStatus.ACTIVE, expiryThreshold);

        for (ChatSession session : expiredSessions) {
            session.setStatus(SessionStatus.EXPIRED);
            sessionRepository.save(session);
            sessionCache.remove(session.getSessionToken());
            log.info("Session expired: {} (user: {})", session.getSessionToken(), session.getUserId());
        }

        if (!expiredSessions.isEmpty()) {
            log.info("Cleaned up {} expired sessions", expiredSessions.size());
        }
    }

    private void cacheSession(ChatSession session) {
        sessionCache.put(session.getSessionToken(), new CachedSession(session, LocalDateTime.now()));
    }

    private boolean isExpired(ChatSession session) {
        return session.getLastActivity().plusMinutes(sessionTimeoutMinutes).isBefore(LocalDateTime.now());
    }

    private boolean isExpired(CachedSession cached) {
        return cached.cachedAt.plusMinutes(sessionTimeoutMinutes).isBefore(LocalDateTime.now());
    }

    private void expireSession(ChatSession session) {
        session.setStatus(SessionStatus.EXPIRED);
        sessionRepository.save(session);
        sessionCache.remove(session.getSessionToken());
    }

    private String createInitialContext() {
        try {
            ObjectNode context = objectMapper.createObjectNode();
            context.put("currentPage", "unknown");
            context.put("lastAction", "session_start");
            context.put("messageCount", 0);
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            return "{}";
        }
    }

    // Cache entry with timestamp
    private static class CachedSession {
        final ChatSession session;
        final LocalDateTime cachedAt;

        CachedSession(ChatSession session, LocalDateTime cachedAt) {
            this.session = session;
            this.cachedAt = cachedAt;
        }
    }
    /**
     * Récupère une session avec ses messages chargés (pour éviter LazyInitializationException)
     */
    /**
     * Récupère une session avec ses messages chargés (pour éviter LazyInitializationException)
     */
    @Transactional(readOnly = true)
    public Optional<ChatSession> getSessionWithMessages(String sessionToken) {
        // Vérifier le cache d'abord
        CachedSession cached = sessionCache.get(sessionToken);
        if (cached != null && !isExpired(cached)) {
            log.debug("Session {} found in cache", sessionToken);
            // Si en cache, charger depuis la DB avec JOIN FETCH pour éviter les problèmes Lazy
            // Ne pas utiliser Hibernate.initialize sur l'objet en cache
            return sessionRepository.findBySessionTokenAndStatusWithMessages(sessionToken, SessionStatus.ACTIVE);
        }

        // Cache miss, charger depuis la DB avec JOIN FETCH
        Optional<ChatSession> sessionOpt = sessionRepository.findBySessionTokenAndStatusWithMessages(sessionToken, SessionStatus.ACTIVE);

        sessionOpt.ifPresent(session -> {
            if (!isExpired(session)) {
                cacheSession(session);
            } else {
                expireSession(session);
            }
        });

        return sessionOpt;
    }
}