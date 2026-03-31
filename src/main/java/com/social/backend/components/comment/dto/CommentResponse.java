package com.social.backend.components.comment.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommentResponse {

    private Long id;
    private Long postId;
    private Long parentId;      // null = commento principale
    private Long authorId;
    private String authorUsername;
    private String authorAvatarUrl;
    private String content;
    private String imageUrl; // null se commento solo testo
    private Instant createdAt;
    private Instant updatedAt;
    private Map<String, Long> reactions;
    private String myReaction;
    private List<CommentResponse> replies; // risposte dirette
}