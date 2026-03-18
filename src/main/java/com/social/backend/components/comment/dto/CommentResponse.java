package com.social.backend.components.comment.dto;

import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommentResponse {

    private Long id;
    private Long postId;
    private Long authorId;
    private String authorUsername;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;

}