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
public class MessageDTO {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderUsername;
    private String senderAvatarUrl;
    private String content;
    private String imageUrl;
    private Boolean isRead;
    private LocalDateTime createdAt;
}