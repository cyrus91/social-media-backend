package com.social.backend.components.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderUsername;
    private String senderAvatarUrl;
    private String content;
    private String imageUrl;
    private String audioUrl;

    // Reply
    private Long replyToId;
    private String replyToContent;
    private String replyToSenderUsername;

    // Stato
    private Boolean isRead;
    private Boolean deletedForAll;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    // Reazioni: mappa emoji → count (es. {"❤️": 2, "👍": 1})
    private Map<String, Long> reactions;
    // Emoji messa dall'utente corrente (null se nessuna)
    private String myReaction;
}