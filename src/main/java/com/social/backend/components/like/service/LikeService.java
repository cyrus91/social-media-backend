package com.social.backend.components.like.service;

import com.social.backend.components.like.dto.LikeRequest;
import com.social.backend.components.like.dto.LikeResponse;
import com.social.backend.components.post.dto.PostResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LikeService {

    LikeResponse create(Long userId, LikeRequest request);

    void delete(Long userId, Long postId);

    List<LikeResponse> listByPost(Long postId);

    int countByPost(Long postId);

    boolean hasUserLikedPost(Long userId, Long postId);

    Page<LikeResponse> listByPost(Long postId, Pageable pageable);

    Page<PostResponse> getLikedPostsByUser(Long userId, Long currentUserId, Pageable pageable);
}