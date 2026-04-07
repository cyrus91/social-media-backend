package com.social.backend.components.bookmark.controller;

import com.social.backend.components.bookmark.service.BookmarkService;
import com.social.backend.components.post.dto.PostResponse;
import com.social.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    // POST /api/bookmarks/{postId}/toggle
    @PostMapping("/{postId}/toggle")
    public ResponseEntity<Map<String, Boolean>> toggle(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId) {
        boolean added = bookmarkService.toggle(userDetails.getUser().getId(), postId);
        return ResponseEntity.ok(Map.of("bookmarked", added));
    }

    // GET /api/bookmarks — bookmark dell'utente corrente
    @GetMapping
    public ResponseEntity<Page<PostResponse>> getMyBookmarks(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = userDetails.getUser().getId();
        return ResponseEntity.ok(bookmarkService.getUserBookmarks(userId, userId, page, size));
    }

    // GET /api/bookmarks/check/{postId} — controlla se bookmarkato
    @GetMapping("/check/{postId}")
    public ResponseEntity<Map<String, Boolean>> check(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId) {
        boolean bookmarked = bookmarkService.isBookmarked(userDetails.getUser().getId(), postId);
        return ResponseEntity.ok(Map.of("bookmarked", bookmarked));
    }
}