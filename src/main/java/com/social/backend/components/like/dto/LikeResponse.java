package com.social.backend.components.like.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LikeResponse {
    private Long userId;
    private String username;
    private String avatarUrl;
    private Long postId;
    private Instant createdAt;
}