// Il content diventa opzionale se c'è un'immagine

package com.social.backend.components.post.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePostRequest {

    @Size(max = 1000, message = "Il contenuto non può superare i 1000 caratteri")
    private String content;  // Rimuovi @NotBlank - può essere vuoto se c'è immagine

    private String imageUrl;
}