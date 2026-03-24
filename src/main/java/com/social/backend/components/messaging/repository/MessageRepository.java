package com.social.backend.components.messaging.repository;

import com.social.backend.components.messaging.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId, Pageable pageable);

    // Conta messaggi non letti per un utente in una conversazione
    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :convId " +
            "AND m.sender.id != :userId AND m.isRead = false")
    long countUnread(@Param("convId") Long convId, @Param("userId") Long userId);

    // Conta totale messaggi non letti per un utente
    @Query("SELECT COUNT(m) FROM Message m WHERE " +
            "(m.conversation.user1.id = :userId OR m.conversation.user2.id = :userId) " +
            "AND m.sender.id != :userId AND m.isRead = false")
    long countAllUnreadForUser(@Param("userId") Long userId);

    // Segna tutti i messaggi come letti in una conversazione
    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.isRead = true WHERE m.conversation.id = :convId " +
            "AND m.sender.id != :userId AND m.isRead = false")
    void markAllAsRead(@Param("convId") Long convId, @Param("userId") Long userId);

    // Ultimo messaggio di una conversazione
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :convId " +
            "ORDER BY m.createdAt DESC")
    Page<Message> findLastMessage(@Param("convId") Long convId, Pageable pageable);
}