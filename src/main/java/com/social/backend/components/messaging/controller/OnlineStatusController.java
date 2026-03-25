package com.social.backend.components.messaging.controller;

import com.social.backend.components.messaging.service.impl.MessagingServiceImpl;
import com.social.backend.components.user.repository.UserRepository;
import com.social.backend.config.RedisPubSubService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
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
        String username = extractUsername(event.getMessage());
        if (username == null) return;

        userRepository.findByUsername(username).ifPresent(user -> {
            int count = sessionCount
                    .computeIfAbsent(user.getId(), id -> new AtomicInteger(0))
                    .incrementAndGet();
            if (count == 1) {
                messagingService.setOnline(user.getId());
                redisPubSubService.publish("/topic/online-status",
                        Map.of("userId", user.getId(), "online", true));
            }
        });
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String username = extractUsername(event.getMessage());
        if (username == null) return;

        userRepository.findByUsername(username).ifPresent(user -> {
            AtomicInteger counter = sessionCount.get(user.getId());
            if (counter == null) return;
            int count = counter.decrementAndGet();
            if (count <= 0) {
                sessionCount.remove(user.getId());
                messagingService.setOffline(user.getId());
                redisPubSubService.publish("/topic/online-status",
                        Map.of("userId", user.getId(), "online", false));
            }
        });
    }

    private String extractUsername(org.springframework.messaging.Message<?> message) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getUser() == null) return null;

        // accessor.getUser() è un UsernamePasswordAuthenticationToken
        // il Principal dentro è un oggetto UserDetails — usiamo getUsername()
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            Object principal = auth.getPrincipal();
            if (principal instanceof UserDetails ud) {
                return ud.getUsername();
            }
            return principal.toString();
        }
        // Fallback per altri tipi di Principal
        return accessor.getUser().getName();
    }
}