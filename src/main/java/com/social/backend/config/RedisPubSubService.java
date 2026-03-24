package com.social.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Gestisce la pubblicazione e ricezione di messaggi tramite Redis Pub/Sub.
 * Pattern:
 *   - Publish: ogni backend pubblica su Redis quando deve inviare un messaggio WebSocket
 *   - Subscribe: ogni backend ascolta Redis e fa il broadcast locale tramite SimpMessagingTemplate
 * Questo garantisce che tutti i client connessi a qualsiasi istanza ricevano il messaggio.
 */
@Service
public class RedisPubSubService implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisPubSubService.class);

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate stompTemplate;
    private final ObjectMapper objectMapper;

    public RedisPubSubService(StringRedisTemplate redisTemplate,
                              SimpMessagingTemplate stompTemplate,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.stompTemplate = stompTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Pubblica un messaggio su Redis.
     * Il channel è nella forma "ws:/queue/messages/123" o "ws:/queue/notifications/456"
     */
    public void publish(String destination, Object payload) {
        try {
            String channel = "ws:" + destination;
            String json = objectMapper.writeValueAsString(
                    new RedisMessage(destination, objectMapper.writeValueAsString(payload))
            );
            redisTemplate.convertAndSend(channel, json);
        } catch (Exception e) {
            log.error("Errore pubblicazione Redis: {}", e.getMessage());
            // Fallback: invia direttamente via STOMP locale
            stompTemplate.convertAndSend(destination, payload);
        }
    }

    /**
     * Riceve messaggi da Redis e li fa il broadcast locale tramite STOMP.
     * Chiamato automaticamente per ogni messaggio sul pattern "ws:*"
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            RedisMessage msg = objectMapper.readValue(json, RedisMessage.class);
            // Broadcast a tutti i client connessi a questa istanza
            Object payload = objectMapper.readValue(msg.payload(), Object.class);
            stompTemplate.convertAndSend(msg.destination(), payload);
        } catch (Exception e) {
            log.error("Errore ricezione Redis: {}", e.getMessage());
        }
    }

    public record RedisMessage(String destination, String payload) {}
}