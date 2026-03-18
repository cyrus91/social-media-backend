package com.social.backend.components.comment.controller;

import com.social.backend.components.comment.dto.CommentResponse;
import com.social.backend.components.comment.dto.CreateCommentRequest;
import com.social.backend.components.comment.dto.UpdateCommentRequest;
import com.social.backend.components.comment.service.CommentService;
import com.social.backend.components.user.entity.User;
import com.social.backend.security.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        User currentUser = userDetails.getUser();
        return commentService.create(currentUser.getId(), request);
    }

    @GetMapping("/{id}")
    public CommentResponse getById(@PathVariable Long id) {
        return commentService.getById(id);
    }

    // GET commenti per post con paginazione
    @GetMapping("/post/{postId}")
    public Page<CommentResponse> listByPost(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return commentService.listByPost(postId, pageable);
    }

    @PutMapping("/{id}")
    public CommentResponse update(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommentRequest request) {
        User currentUser = userDetails.getUser();
        return commentService.update(currentUser.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id) {
        User currentUser = userDetails.getUser();
        commentService.delete(currentUser.getId(), id);
    }
}