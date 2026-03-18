package com.social.backend.components.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data
public class UpdateCommentRequest {

    @NotBlank(message = "Il contenuto non può essere vuoto")
    @Size(max = 500, message = "Il contenuto non può superare 500 caratteri")
    private String content;
}