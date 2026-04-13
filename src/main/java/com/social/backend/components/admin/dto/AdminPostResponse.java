package com.social.backend.components.admin.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminPostResponse {
    private Long id;
    private String content;
    private String authorUsername;
    private Long authorId;
    private LocalDateTime createdAt;
    private int commentCount;
}