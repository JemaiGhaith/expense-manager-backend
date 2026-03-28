// ChatSessionRepository.java
package com.coralio.chatbotmicroservice.repository;

import com.coralio.chatbotmicroservice.entity.ChatSession;
import com.coralio.chatbotmicroservice.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Optional<ChatSession> findBySessionTokenAndStatus(String sessionToken, SessionStatus status);

    List<ChatSession> findByUserIdAndStatus(String userId, SessionStatus status);

    @Query("SELECT s FROM ChatSession s WHERE s.status = :status AND s.lastActivity < :cutoff")
    List<ChatSession> findByStatusAndLastActivityBefore(@Param("status") SessionStatus status,
                                                        @Param("cutoff") LocalDateTime cutoff);

    long countByUserIdAndStatus(String userId, SessionStatus status);
}