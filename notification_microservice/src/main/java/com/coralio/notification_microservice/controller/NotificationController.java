package com.coralio.notification_microservice.controller;

import com.coralio.notification_microservice.dto.request.CreateNotificationRequest;
import com.coralio.notification_microservice.dto.response.NotificationResponse;
import com.coralio.notification_microservice.dto.response.UnreadCountResponse;
import com.coralio.notification_microservice.service.NotificationService;
import com.coralio.notification_microservice.service.WebSocketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class NotificationController {

    private final NotificationService notificationService;
    private final WebSocketService webSocketService;

    // For other microservices to call
    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(
            @Valid @RequestBody CreateNotificationRequest request) {
        return ResponseEntity.ok(notificationService.createNotification(request));
    }

    // User endpoints
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getUserNotifications(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, limit));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(new UnreadCountResponse(notificationService.getUnreadCount(userId)));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID notificationId,
            @RequestHeader("X-User-Id") UUID userId) {
        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@RequestHeader("X-User-Id") UUID userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable UUID notificationId,
            @RequestHeader("X-User-Id") UUID userId) {
        notificationService.deleteNotification(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    // com/coralio/notification_microservice/controller/NotificationController.java
    @GetMapping("/stream")
    public SseEmitter streamNotifications(@RequestParam("userId") UUID userId) {
        return webSocketService.createEmitter(userId);
    }
}