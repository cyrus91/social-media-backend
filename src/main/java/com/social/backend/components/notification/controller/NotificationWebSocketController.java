package com.social.backend.components.notification.controller;

import com.social.backend.components.notification.dto.NotificationResponse;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class NotificationWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationWebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Invia una notifica real-time a un utente specifico
     */
    public void sendNotificationToUser(Long userId, NotificationResponse notification) {
        // Invia al topic /queue/notifications/{userId}
        messagingTemplate.convertAndSend(
                "/queue/notifications/" + userId,
                notification
        );
    }

    /**
     * Broadcast di una notifica a tutti gli utenti connessi (opzionale)
     */
    public void broadcastNotification(NotificationResponse notification) {
        messagingTemplate.convertAndSend("/topic/notifications", notification);
    }
}