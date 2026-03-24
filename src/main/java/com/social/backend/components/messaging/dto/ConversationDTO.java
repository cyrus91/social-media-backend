package com.social.backend.components.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {
    private Long id;
    // L'altro utente (non quello loggato)
    private Long otherUserId;
    private String otherUsername;
    private String otherAvatarUrl;
    private Boolean otherOnline;
    // Ultimo messaggio
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private Long unreadCount;
}