package com.social.backend.components.notification.enums;

public enum NotificationType {
    LIKE,       // Like a un post
    COMMENT,    // Commento a un post o risposta a un commento
    FOLLOW,     // Qualcuno ha iniziato a seguirti
    REACTION,   // Reazione a un commento
    MENTION,    // Tag @utente in post o commento
    REPORT      // Segnalazione post (solo per admin)
}