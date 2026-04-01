package com.coralio.notification_microservice.service;

import com.coralio.notification_microservice.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class WebSocketService {

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(UUID userId) {
        SseEmitter emitter = new SseEmitter(30000L);

        emitter.onTimeout(() -> {
            log.debug("Timeout for user: {}", userId);
            emitters.remove(userId);
        });

        emitter.onCompletion(() -> {
            log.debug("Complete for user: {}", userId);
            emitters.remove(userId);
        });

        emitter.onError((e) -> {
            log.error("Error for user: {}", userId, e);
            emitters.remove(userId);
        });

        emitters.put(userId, emitter);

        try {
            emitter.send(SseEmitter.event().name("connected").data("Connected"));
        } catch (Exception e) {
            log.error("Failed to send connection event", e);
        }

        return emitter;
    }

    public void sendToUser(UUID userId, Notification notification) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notification));
                log.debug("Sent to user: {}", userId);
            } catch (Exception e) {
                log.error("Failed to send", e);
                emitters.remove(userId);
            }
        }
    }
}