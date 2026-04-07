package com.coralio.notification_microservice.service;


import com.coralio.notification_microservice.dto.request.CreateNotificationRequest;
import com.coralio.notification_microservice.dto.response.NotificationResponse;
import com.coralio.notification_microservice.entity.Notification;
import com.coralio.notification_microservice.entity.UserPreference;
import com.coralio.notification_microservice.enums.NotificationPriority;
import com.coralio.notification_microservice.enums.NotificationStatus;
import com.coralio.notification_microservice.enums.NotificationType;
import com.coralio.notification_microservice.repository.NotificationRepository;
import com.coralio.notification_microservice.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final EmailService emailService;
    private final WebSocketService webSocketService;

    @Transactional
    public NotificationResponse createNotification(CreateNotificationRequest request) {
        // Get user preferences
        UserPreference preferences = preferenceRepository.findByUserId(request.getUserId())
                .orElseGet(() -> createDefaultPreferences(request.getUserId()));

        // Check if user wants this type
        NotificationType type = NotificationType.valueOf(request.getType());
        if (!isTypeEnabled(preferences, type)) {
            log.info("Notification type {} disabled for user {}", type, request.getUserId());
            return null;
        }

        // Build notification
        // Build notification
        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .userEmail(request.getUserEmail())
                .type(type)
                .title(request.getTitle())
                .message(request.getMessage())
                .data(request.getData())
                .priority(request.getPriority() != null ?
                        NotificationPriority.valueOf(request.getPriority()) :
                        NotificationPriority.NORMAL)
                .status(NotificationStatus.PENDING)
                .sourceService(request.getSourceService())
                .sourceEntityId(request.getSourceEntityId())
                .sourceEntityType(request.getSourceEntityType())
                .retryCount(0)
                .createdAt(LocalDateTime.now())  // ✅ Explicitly set
                .build();

        // Set expiration
        if (request.getExpiresInHours() != null) {
            notification.setExpiresAt(LocalDateTime.now().plusHours(request.getExpiresInHours()));
        } else {
            notification.setExpiresAt(LocalDateTime.now().plusDays(30));
        }

        notification = notificationRepository.save(notification);

        // Send async
        sendNotificationAsync(notification, request.getSendEmail() != null && request.getSendEmail());

        return mapToResponse(notification);
    }

    @Async
    public void sendNotificationAsync(Notification notification, boolean forceEmail) {
        try {
            notification.setStatus(NotificationStatus.PROCESSING);
            notificationRepository.save(notification);

            // Send WebSocket
            webSocketService.sendToUser(notification.getUserId(), notification);

            // Send email if needed
            if (forceEmail || notification.getPriority() == NotificationPriority.HIGH ||
                    notification.getPriority() == NotificationPriority.CRITICAL) {
                emailService.sendNotificationEmail(notification);
            }

            notification.setStatus(NotificationStatus.SENT);
            notificationRepository.save(notification);
            log.info("Notification {} sent", notification.getId());

        } catch (Exception e) {
            log.error("Failed to send notification: {}", notification.getId(), e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
            notification.setRetryCount(notification.getRetryCount() + 1);
            notificationRepository.save(notification);
        }
    }

    public List<NotificationResponse> getUserNotifications(UUID userId, int limit) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public long getUnreadCount(UUID userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        notificationRepository.markAsRead(userId, notificationId);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Transactional
    public void deleteNotification(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized");
        }

        notificationRepository.delete(notification);
    }

    private UserPreference createDefaultPreferences(UUID userId) {
        UserPreference preferences = new UserPreference();
        preferences.setUserId(userId);
        preferences.setEmailEnabled(true);
        preferences.setPushEnabled(true);
        preferences.setCreatedAt(LocalDateTime.now());
        preferences.setUpdatedAt(LocalDateTime.now());
        return preferenceRepository.save(preferences);
    }

    private boolean isTypeEnabled(UserPreference preferences, NotificationType type) {
        if (preferences.getTypePreferences() == null) return true;
        Object enabled = preferences.getTypePreferences().get(type.name());
        return enabled == null || (Boolean) enabled;
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .type(notification.getType().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .data(notification.getData())
                .read(notification.isRead())
                .priority(notification.getPriority().name())
                .status(notification.getStatus().name())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}