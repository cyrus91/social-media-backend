package com.social.backend.components.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {
    private Long id;
    private String content;

    // NUOVO: Lista di immagini con ID (usare per operazioni di modifica/eliminazione)
    private List<PostImageDto> images;

    // RETROCOMPATIBILITÀ: Lista di sole URL
    private List<String> imageUrls;

    // DEPRECATO: Mantieni per retrocompatibilità frontend
    @Deprecated
    private String imageUrl;

    private Long authorId;
    private String authorUsername;
    private String authorAvatarUrl;
    private Instant createdAt;
    private Instant updatedAt;
    private Integer likeCount;
    private Integer commentCount;
    private Long viewCount;
    private Boolean liked;
}