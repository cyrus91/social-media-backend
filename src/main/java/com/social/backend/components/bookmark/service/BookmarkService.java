package com.social.backend.components.bookmark.service;

import com.social.backend.components.post.dto.PostResponse;
import org.springframework.data.domain.Page;

public interface BookmarkService {

    boolean toggle(Long userId, Long postId);

    boolean isBookmarked(Long userId, Long postId);

    Page<PostResponse> getUserBookmarks(Long userId, Long currentUserId, int page, int size);
}