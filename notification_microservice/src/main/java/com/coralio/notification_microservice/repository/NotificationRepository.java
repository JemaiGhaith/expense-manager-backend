package com.coralio.notification_microservice.repository;



import com.coralio.notification_microservice.entity.Notification;
import com.coralio.notification_microservice.enums.NotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.read = false AND (n.expiresAt IS NULL OR n.expiresAt > CURRENT_TIMESTAMP)")
    long countUnreadByUserId(@Param("userId") UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.id = :notificationId")
    int markAsRead(@Param("userId") UUID userId, @Param("notificationId") UUID notificationId);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.read = false")
    int markAllAsRead(@Param("userId") UUID userId);

    List<Notification> findByStatusAndRetryCountLessThanAndExpiresAtAfter(
            NotificationStatus status,
            int maxRetries,
            LocalDateTime now
    );

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.status = 'EXPIRED' WHERE n.expiresAt < CURRENT_TIMESTAMP AND n.status = 'PENDING'")
    int expireOldNotifications();

    void deleteByExpiresAtBefore(LocalDateTime date);
}