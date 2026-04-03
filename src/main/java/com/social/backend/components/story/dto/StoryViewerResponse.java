package com.social.backend.components.story.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StoryViewerResponse {
    private Long userId;
    private String username;
    private String avatarUrl;
    private LocalDateTime viewedAt;
}