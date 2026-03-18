package com.social.backend.components.like.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LikeRequest {

    @NotNull(message = "Il postId è obbligatorio")
    @NotNull
    private Long postId;
}