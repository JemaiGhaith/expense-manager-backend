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
    // NotificationController.java - Modifier pour accepter userId en query param
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getUserNotifications(
            @RequestParam UUID userId,  // ✅ Changer de header à param
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, limit));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @RequestParam UUID userId) {  // ✅ Changer de header à param
        return ResponseEntity.ok(new UnreadCountResponse(notificationService.getUnreadCount(userId)));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID notificationId,
            @RequestParam UUID userId) {  // ✅ Changer de header à param
        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@RequestParam UUID userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable UUID notificationId,
            @RequestParam UUID userId) {   // ← changement : @RequestParam au lieu de @RequestHeader
        notificationService.deleteNotification(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    // com/coralio/notification_microservice/controller/NotificationController.java
    @GetMapping("/stream")
    public SseEmitter streamNotifications(@RequestParam("userId") UUID userId) {
        return webSocketService.createEmitter(userId);
    }
}