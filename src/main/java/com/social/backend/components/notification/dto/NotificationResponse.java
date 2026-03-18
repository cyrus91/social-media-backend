package com.social.backend.components.notification.dto;

import com.social.backend.components.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private Long id;
    private Long actorId;
    private String actorUsername;
    private String actorAvatarUrl;
    private NotificationType type;
    private Long postId;
    private Long commentId;
    private String message;
    private Boolean isRead;
    private Instant createdAt;
}