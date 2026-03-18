package com.social.backend.components.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCommentRequest {

    @NotNull(message = "Il postId è obbligatorio")
    private Long postId;

    @NotBlank(message = "Il contenuto non può essere vuoto")
    @Size(max = 500, message = "Il contenuto non può superare 500 caratteri")
    private String content;
}