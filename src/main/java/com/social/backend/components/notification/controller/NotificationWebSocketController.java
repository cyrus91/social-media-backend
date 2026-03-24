package com.social.backend.components.notification.controller;

import com.social.backend.components.notification.dto.NotificationResponse;
import com.social.backend.config.RedisPubSubService;
import org.springframework.stereotype.Controller;

@Controller
public class NotificationWebSocketController {

    private final RedisPubSubService redisPubSubService;

    public NotificationWebSocketController(RedisPubSubService redisPubSubService) {
        this.redisPubSubService = redisPubSubService;
    }

    /**
     * Invia una notifica real-time a un utente specifico via Redis Pub/Sub.
     * Il messaggio viene distribuito a tutte le istanze backend.
     */
    public void sendNotificationToUser(Long userId, NotificationResponse notification) {
        redisPubSubService.publish("/queue/notifications/" + userId, notification);
    }

    public void broadcastNotification(NotificationResponse notification) {
        redisPubSubService.publish("/topic/notifications", notification);
    }
}