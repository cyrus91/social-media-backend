package com.social.backend.components.messaging.controller;

import com.social.backend.components.messaging.service.impl.MessagingServiceImpl;
import com.social.backend.components.user.repository.UserRepository;
import com.social.backend.config.RedisPubSubService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OnlineStatusController {

    private final MessagingServiceImpl messagingService;
    private final RedisPubSubService redisPubSubService;
    private final UserRepository userRepository;

    // Conta quante sessioni WebSocket attive ha ogni utente
    // Un utente è offline solo quando tutte le sue sessioni si disconnettono
    private final ConcurrentHashMap<Long, AtomicInteger> sessionCount = new ConcurrentHashMap<>();

    public OnlineStatusController(MessagingServiceImpl messagingService,
                                  RedisPubSubService redisPubSubService,
                                  UserRepository userRepository) {
        this.messagingService = messagingService;
        this.redisPubSubService = redisPubSubService;
        this.userRepository = userRepository;
    }

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = getUsername(accessor);
        if (username == null) return;

        userRepository.findByUsername(username).ifPresent(user -> {
            int count = sessionCount
                    .computeIfAbsent(user.getId(), id -> new AtomicInteger(0))
                    .incrementAndGet();

            // Setta online solo alla prima connessione
            if (count == 1) {
                messagingService.setOnline(user.getId());
                redisPubSubService.publish("/topic/online-status",
                        Map.of("userId", user.getId(), "online", true));
            }
        });
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = getUsername(accessor);
        if (username == null) return;

        userRepository.findByUsername(username).ifPresent(user -> {
            AtomicInteger counter = sessionCount.get(user.getId());
            if (counter == null) return;

            int count = counter.decrementAndGet();

            // Setta offline solo quando TUTTE le sessioni si disconnettono
            if (count <= 0) {
                sessionCount.remove(user.getId());
                messagingService.setOffline(user.getId());
                redisPubSubService.publish("/topic/online-status",
                        Map.of("userId", user.getId(), "online", false));
            }
        });
    }

    private String getUsername(StompHeaderAccessor accessor) {
        if (accessor.getUser() != null) return accessor.getUser().getName();
        Object login = accessor.getNativeHeader("login");
        if (login instanceof java.util.List<?> list && !list.isEmpty()) {
            return list.get(0).toString();
        }
        return null;
    }
}