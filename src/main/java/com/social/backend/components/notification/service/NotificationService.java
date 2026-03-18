package com.social.backend.components.notification.service;

import com.social.backend.components.notification.dto.NotificationResponse;
import com.social.backend.components.notification.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    /**
     * Crea una nuova notifica
     */
    void createNotification(Long recipientId, Long actorId, NotificationType type,
                            Long postId, Long commentId, String message);

    /**
     * Ottieni tutte le notifiche di un utente (paginate)
     */
    Page<NotificationResponse> getNotifications(Long userId, Pageable pageable);

    /**
     * Ottieni solo le notifiche non lette
     */
    Page<NotificationResponse> getUnreadNotifications(Long userId, Pageable pageable);

    /**
     * Conta le notifiche non lette
     */
    Long countUnreadNotifications(Long userId);

    /**
     * Marca una notifica come letta
     */
    void markAsRead(Long userId, Long notificationId);

    /**
     * Marca tutte le notifiche come lette
     */
    void markAllAsRead(Long userId);

    /**
     * Elimina una notifica
     */
    void deleteNotification(Long userId, Long notificationId);
}