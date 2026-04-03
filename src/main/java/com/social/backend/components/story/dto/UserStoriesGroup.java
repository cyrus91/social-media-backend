package com.social.backend.components.story.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Raggruppa le storie di un singolo utente per la Stories Bar.
 * hasUnread = true se almeno una storia non è stata vista dall'utente corrente.
 */
@Data
@Builder
public class UserStoriesGroup {
    private Long authorId;
    private String authorUsername;
    private String authorAvatarUrl;
    private List<StoryResponse> stories;
    private boolean hasUnread;
    private boolean isOwn; // true se sono le storie dell'utente loggato
}