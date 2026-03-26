package com.social.backend.components.messaging.repository;

import com.social.backend.components.messaging.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // Tutti i messaggi di una conversazione in ordine cronologico (nessun limite)
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId ORDER BY m.createdAt ASC")
    List<Message> findAllByConversationId(@Param("conversationId") Long conversationId);

    // Ultimo messaggio di una conversazione (per preview)
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :convId ORDER BY m.createdAt DESC LIMIT 1")
    Optional<Message> findLastMessage(@Param("convId") Long convId);

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

    // Carica messaggio con reactions in modo esplicito (evita cache EAGER stale)
    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.reactions r LEFT JOIN FETCH r.user WHERE m.id = :id")
    Optional<Message> findByIdWithReactions(@Param("id") Long id);
    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.deletedForAll = true, m.content = null, m.imageUrl = null, m.audioUrl = null " +
            "WHERE m.expiresAt IS NOT NULL AND m.expiresAt < CURRENT_TIMESTAMP AND m.deletedForAll = false")
    int markExpiredAsDeleted();
}