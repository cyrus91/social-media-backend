package com.social.backend.components.messaging.repository;

import com.social.backend.components.messaging.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // Trova conversazione tra due utenti (in qualsiasi ordine)
    @Query("SELECT c FROM Conversation c WHERE " +
            "(c.user1.id = :userId1 AND c.user2.id = :userId2) OR " +
            "(c.user1.id = :userId2 AND c.user2.id = :userId1)")
    Optional<Conversation> findBetweenUsers(@Param("userId1") Long userId1,
                                            @Param("userId2") Long userId2);

    // Tutte le conversazioni di un utente ordinate per ultimo messaggio
    @Query("SELECT c FROM Conversation c WHERE c.user1.id = :userId OR c.user2.id = :userId " +
            "ORDER BY c.updatedAt DESC")
    List<Conversation> findByUserId(@Param("userId") Long userId);

    // Aggiorna solo updatedAt senza toccare i messaggi (evita orphanRemoval bug)
    @Modifying
    @Transactional
    @Query("UPDATE Conversation c SET c.updatedAt = CURRENT_TIMESTAMP WHERE c.id = :id")
    void touchUpdatedAt(@Param("id") Long id);
}