package com.social.backend.components.comment.controller;

import com.social.backend.components.comment.dto.CommentResponse;
import com.social.backend.components.comment.dto.CreateCommentRequest;
import com.social.backend.components.comment.dto.UpdateCommentRequest;
import com.social.backend.components.comment.service.CommentService;
import com.social.backend.security.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody CreateCommentRequest request) {
        return commentService.create(userDetails.getUser().getId(), request);
    }

    @GetMapping("/{id}")
    public CommentResponse getById(@PathVariable Long id) {
        return commentService.getById(id);
    }

    @GetMapping("/post/{postId}")
    public Page<CommentResponse> listByPost(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Long userId = userDetails != null ? userDetails.getUser().getId() : null;
        return commentService.listByPost(postId, pageable, userId);
    }

    @PutMapping("/{id}")
    public CommentResponse update(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommentRequest request) {
        return commentService.update(userDetails.getUser().getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id) {
        commentService.delete(userDetails.getUser().getId(), id);
    }

    @PostMapping("/{id}/reactions")
    public CommentResponse toggleReaction(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return commentService.toggleReaction(id, userDetails.getUser().getId(), body.get("emoji"));
    }
}