package com.social.backend.components.comment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCommentRequest {

    @NotNull(message = "Il postId è obbligatorio")
    private Long postId;

    @Size(max = 500, message = "Il contenuto non può superare 500 caratteri")
    private String content; // opzionale se presente immagine

    private String imageUrl; // opzionale — URL Cloudinary già uploadato

    // Opzionale — se presente è una risposta a un commento esistente
    private Long parentId;

    // Opzionale — ID del commento specifico a cui si sta rispondendo (per notifiche corrette
    // nelle risposte annidate, dove parentId punta al root ma replyToCommentId punta all'autore da notificare)
    private Long replyToCommentId;
}