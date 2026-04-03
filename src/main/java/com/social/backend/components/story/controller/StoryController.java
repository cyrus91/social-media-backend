package com.social.backend.components.story.controller;

import com.social.backend.components.story.dto.StoryResponse;
import com.social.backend.components.story.dto.StoryViewerResponse;
import com.social.backend.components.story.dto.UserStoriesGroup;
import com.social.backend.components.story.service.StoryService;
import com.social.backend.components.user.entity.User;
import com.social.backend.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stories")
@Tag(name = "Stories", description = "Storie 24h")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

    // ==================== CREATE ====================

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea storia", description = "Carica una foto o video come storia (scade in 24h)")
    public StoryResponse createStory(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestPart("media") MultipartFile media,
            @RequestPart(value = "caption", required = false) String caption) {

        User user = userDetails.getUser();
        return storyService.createStory(user.getId(), media, caption);
    }

    // ==================== READ ====================

    @GetMapping("/feed")
    @Operation(summary = "Feed storie", description = "Storie degli utenti seguiti + proprie")
    public List<UserStoriesGroup> getFeedStories(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return storyService.getFeedStories(userDetails.getUser().getId());
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Storie utente", description = "Storie attive di un utente specifico")
    public List<StoryResponse> getUserStories(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long currentUserId = userDetails != null ? userDetails.getUser().getId() : -1L;
        return storyService.getUserStories(userId, currentUserId);
    }

    @GetMapping("/{storyId}/viewers")
    @Operation(summary = "Chi ha visto", description = "Lista di chi ha visualizzato la storia (solo autore)")
    public List<StoryViewerResponse> getViewers(
            @PathVariable Long storyId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return storyService.getViewers(storyId, userDetails.getUser().getId());
    }

    // ==================== MARK VIEWED ====================

    @PostMapping("/{storyId}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Segna come vista")
    public void markAsViewed(
            @PathVariable Long storyId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        storyService.markAsViewed(storyId, userDetails.getUser().getId());
    }

    // ==================== DELETE ====================

    @DeleteMapping("/{storyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina storia")
    public void deleteStory(
            @PathVariable Long storyId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        storyService.deleteStory(storyId, userDetails.getUser().getId());
    }
}