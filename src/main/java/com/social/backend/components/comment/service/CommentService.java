package com.social.backend.components.comment.service;

import com.social.backend.components.comment.dto.CommentResponse;
import com.social.backend.components.comment.dto.CreateCommentRequest;
import com.social.backend.components.comment.dto.UpdateCommentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CommentService {

    CommentResponse create(Long authorId, CreateCommentRequest request);

    CommentResponse getById(Long id);

    List<CommentResponse> listByPost(Long postId);

    Page<CommentResponse> listByPost(Long postId, Pageable pageable);

    // Con userId per popolare myReaction — usato dal controller autenticato
    Page<CommentResponse> listByPost(Long postId, Pageable pageable, Long userId);

    CommentResponse update(Long currentUserId, Long commentId, UpdateCommentRequest request);

    void delete(Long currentUserId, Long commentId);

    CommentResponse toggleReaction(Long commentId, Long userId, String emoji);

    void deleteImage(Long commentId, Long userId);
}