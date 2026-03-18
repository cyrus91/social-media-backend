package com.social.backend.components.like.controller;

import com.social.backend.components.like.dto.LikeRequest;
import com.social.backend.components.like.dto.LikeResponse;
import com.social.backend.components.like.service.LikeService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/likes")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    // Metti like a un post
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LikeResponse create(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody LikeRequest request) {
        User currentUser = userDetails.getUser();
        return likeService.create(currentUser.getId(), request);
    }

    // Rimuovi like da un post
    @DeleteMapping("/post/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId) {
        User currentUser = userDetails.getUser();
        likeService.delete(currentUser.getId(), postId);
    }

    // GET tutti i like di un post
    @GetMapping("/post/{postId}")
    public List<LikeResponse> listByPost(@PathVariable Long postId) {
        return likeService.listByPost(postId);
    }

    // GET conteggio like di un post
    @GetMapping("/post/{postId}/count")
    public Map<String, Integer> countByPost(@PathVariable Long postId) {
        int count = likeService.countByPost(postId);
        Map<String, Integer> response = new HashMap<>();
        response.put("count", count);
        return response;
    }

    // GET likes con paginazione (per il drawer)
    @GetMapping("/post/{postId}/paginated")
    public Page<LikeResponse> listByPostPaginated(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return likeService.listByPost(postId, pageable);
    }

    // Verifica se l'utente ha messo like
    @GetMapping("/post/{postId}/check")
    public Map<String, Boolean> checkIfLiked(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId) {
        User currentUser = userDetails.getUser();
        boolean hasLiked = likeService.hasUserLikedPost(currentUser.getId(), postId);
        Map<String, Boolean> response = new HashMap<>();
        response.put("liked", hasLiked);
        return response;
    }
}