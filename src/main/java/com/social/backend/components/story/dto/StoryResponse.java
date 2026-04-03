package com.social.backend.components.story.dto;

import com.social.backend.components.story.entity.Story;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StoryResponse {
    private Long id;
    private Long authorId;
    private String authorUsername;
    private String authorAvatarUrl;
    private String mediaUrl;
    private String mediaType;
    private String caption;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean viewed;
    private long viewCount;

    public static StoryResponse from(Story story, boolean viewed, long viewCount) {
        return StoryResponse.builder()
                .id(story.getId())
                .authorId(story.getAuthor().getId())
                .authorUsername(story.getAuthor().getUsername())
                .authorAvatarUrl(story.getAuthor().getAvatarUrl())
                .mediaUrl(story.getMediaUrl())
                .mediaType(story.getMediaType().name())
                .caption(story.getCaption())
                .createdAt(story.getCreatedAt())
                .expiresAt(story.getExpiresAt())
                .viewed(viewed)
                .viewCount(viewCount)
                .build();
    }
}