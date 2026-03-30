package com.social.backend.components.notification.service.impl;

import com.social.backend.common.exception.ForbiddenException;
import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.notification.controller.NotificationWebSocketController;
import com.social.backend.components.notification.dto.NotificationResponse;
import com.social.backend.components.notification.entity.Notification;
import com.social.backend.components.notification.enums.NotificationType;
import com.social.backend.components.notification.repository.NotificationRepository;
import com.social.backend.components.notification.service.NotificationService;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationWebSocketController webSocketController;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   UserRepository userRepository,
                                   NotificationWebSocketController webSocketController) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.webSocketController = webSocketController;
    }

    @Override
    @Transactional
    public void createNotification(Long recipientId, Long actorId, NotificationType type,
                                   Long postId, Long commentId, String message) {
        // Non creare notifica se l'attore è il destinatario (es: like al proprio post)
        if (recipientId.equals(actorId)) {
            return;
        }

        // Per REACTION non serve il duplicate check — viene gestito dal chiamante
        // che cancella la notifica precedente prima di crearne una nuova
        if (type != NotificationType.REACTION) {
            boolean exists = notificationRepository.existsByRecipientIdAndActorIdAndTypeAndPostIdAndCommentId(
                    recipientId, actorId, type, postId, commentId);
            if (exists) {
                return;
            }
        }

        // Trova utenti
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Destinatario non trovato con ID: " + recipientId));

        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + actorId));

        // Crea notifica
        Notification notification = Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type)
                .postId(postId)
                .commentId(commentId)
                .message(message)
                .isRead(false)
                .build();

        Notification savedNotification = notificationRepository.save(notification);

        //  Invia notifica real-time via WebSocket
        NotificationResponse notificationResponse = mapToResponse(savedNotification);
        webSocketController.sendNotificationToUser(recipientId, notificationResponse);
    }

    @Override
    public Page<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        Page<Notification> notifications = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(userId, pageable);

        return notifications.map(this::mapToResponse);
    }

    @Override
    public Page<NotificationResponse> getUnreadNotifications(Long userId, Pageable pageable) {
        Page<Notification> notifications = notificationRepository
                .findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable);

        return notifications.map(this::mapToResponse);
    }

    @Override
    public Long countUnreadNotifications(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notifica non trovata con ID: " + notificationId));

        // Verifica che la notifica appartenga all'utente
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new ForbiddenException("Non puoi modificare una notifica di un altro utente");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByRecipientId(userId);
    }

    @Override
    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notifica non trovata con ID: " + notificationId));

        // Verifica che la notifica appartenga all'utente
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new ForbiddenException("Non puoi eliminare una notifica di un altro utente");
        }

        notificationRepository.delete(notification);
    }

    // Mapping
    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .actorId(notification.getActor().getId())
                .actorUsername(notification.getActor().getUsername())
                .actorAvatarUrl(notification.getActor().getAvatarUrl())
                .type(notification.getType())
                .postId(notification.getPostId())
                .commentId(notification.getCommentId())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .build();
    }
}