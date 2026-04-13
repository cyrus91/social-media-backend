package com.social.backend.components.poll.controller;

import com.social.backend.components.poll.dto.CreatePollRequest;
import com.social.backend.components.poll.dto.PollResponse;
import com.social.backend.components.poll.dto.VoteRequest;
import com.social.backend.components.poll.service.PollService;
import com.social.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    // Crea sondaggio per un post esistente
    @PostMapping("/post/{postId}")
    public ResponseEntity<PollResponse> create(
            @PathVariable Long postId,
            @RequestBody CreatePollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pollService.createForPost(postId, request));
    }

    // Ottieni sondaggio di un post
    @GetMapping("/post/{postId}")
    public ResponseEntity<PollResponse> getByPost(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId) {
        Long userId = userDetails != null ? userDetails.getUser().getId() : null;
        return ResponseEntity.ok(pollService.getByPostId(postId, userId));
    }

    // Vota
    @PostMapping("/{pollId}/vote")
    public ResponseEntity<?> vote(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long pollId,
            @RequestBody VoteRequest request) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        try {
            return ResponseEntity.ok(pollService.vote(pollId, request.getOptionId(), userDetails.getUser().getId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}