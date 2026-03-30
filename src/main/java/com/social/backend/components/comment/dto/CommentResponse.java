package com.social.backend.components.comment.dto;

import lombok.*;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommentResponse {

    private Long id;
    private Long postId;
    private Long authorId;
    private String authorUsername;
    private String authorAvatarUrl;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;

    // Reazioni: emoji → count
    private Map<String, Long> reactions;
    // Emoji messa dall'utente corrente
    private String myReaction;
}