package com.social.backend.components.notification.repository;

import com.social.backend.components.notification.entity.Notification;
import com.social.backend.components.notification.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Trova tutte le notifiche di un utente (ordinate per data decrescente)
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    // Trova solo le notifiche non lette
    Page<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    // Conta le notifiche non lette
    Long countByRecipientIdAndIsReadFalse(Long recipientId);

    // Marca tutte le notifiche come lette
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.id = :recipientId AND n.isRead = false")
    void markAllAsReadByRecipientId(@Param("recipientId") Long recipientId);

    // Verifica se esiste già una notifica identica (per evitare duplicati)
    // Elimina notifica precedente per stesso autore/attore/tipo/commento (evita duplicate check)
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.recipient.id = :recipientId AND n.actor.id = :actorId " +
            "AND n.type = :type AND n.commentId = :commentId")
    void deleteByRecipientActorTypeComment(@Param("recipientId") Long recipientId,
                                           @Param("actorId") Long actorId,
                                           @Param("type") NotificationType type,
                                           @Param("commentId") Long commentId);

    boolean existsByRecipientIdAndActorIdAndTypeAndPostIdAndCommentId(
            Long recipientId, Long actorId, NotificationType type, Long postId, Long commentId);

    // Elimina notifica di FOLLOW (usato quando si fa unfollow)
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.recipient.id = :recipientId AND n.actor.id = :actorId AND n.type = :type")
    void deleteByRecipientIdAndActorIdAndType(
            @Param("recipientId") Long recipientId,
            @Param("actorId") Long actorId,
            @Param("type") NotificationType type);

    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    @Modifying
    @Transactional
    void deleteByRecipientId(Long recipientId);

    @Modifying
    @Transactional
    void deleteByActorId(Long actorId);
}