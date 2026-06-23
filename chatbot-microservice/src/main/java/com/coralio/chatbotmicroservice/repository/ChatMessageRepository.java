// ChatMessageRepository.java
package com.coralio.chatbotmicroservice.repository;

import com.coralio.chatbotmicroservice.entity.ChatMessage;
import com.coralio.chatbotmicroservice.entity.ChatSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session);

    @Query("SELECT m FROM ChatMessage m WHERE m.session = :session ORDER BY m.createdAt DESC")
    List<ChatMessage> findBySessionOrderByCreatedAtDesc(@Param("session") ChatSession session, Pageable pageable);

    default List<ChatMessage> findBySessionOrderByCreatedAtDesc(ChatSession session, int limit) {
        return findBySessionOrderByCreatedAtDesc(session, Pageable.ofSize(limit));
    }

    void deleteBySession(ChatSession session);
}