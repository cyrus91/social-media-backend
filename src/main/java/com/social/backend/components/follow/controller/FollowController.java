package com.social.backend.components.follow.controller;

import com.social.backend.components.follow.dto.FollowRequest;
import com.social.backend.components.follow.dto.FollowResponse;
import com.social.backend.components.follow.service.FollowService;
import com.social.backend.components.user.entity.User;
import com.social.backend.security.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    // Segui un utente
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FollowResponse create(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody FollowRequest request) {
        User currentUser = userDetails.getUser();
        return followService.create(currentUser.getId(), request);
    }

    // Smetti di seguire un utente
    @DeleteMapping("/user/{followedId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long followedId) {
        User currentUser = userDetails.getUser();
        followService.delete(currentUser.getId(), followedId);
    }

    // GET followers di un utente (chi lo segue)
    @GetMapping("/user/{userId}/followers")
    public List<FollowResponse> listFollowers(@PathVariable Long userId) {
        return followService.listFollowers(userId);
    }

    // GET following di un utente (chi sta seguendo)
    @GetMapping("/user/{userId}/following")
    public List<FollowResponse> listFollowing(@PathVariable Long userId) {
        return followService.listFollowing(userId);
    }

    // Conta followers
    @GetMapping("/user/{userId}/followers/count")
    public Map<String, Integer> countFollowers(@PathVariable Long userId) {
        int count = followService.countFollowers(userId);
        Map<String, Integer> response = new HashMap<>();
        response.put("count", count);
        return response;
    }

    // Conta following
    @GetMapping("/user/{userId}/following/count")
    public Map<String, Integer> countFollowing(@PathVariable Long userId) {
        int count = followService.countFollowing(userId);
        Map<String, Integer> response = new HashMap<>();
        response.put("count", count);
        return response;
    }

    // Verifica se stai seguendo un utente (VECCHIO ENDPOINT - Deprecato)
    @GetMapping("/user/{followedId}/check")
    public Map<String, Boolean> checkIfFollowing(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long followedId) {
        User currentUser = userDetails.getUser();
        boolean isFollowing = followService.isFollowing(currentUser.getId(), followedId);
        Map<String, Boolean> response = new HashMap<>();
        response.put("following", isFollowing);
        return response;
    }

    // NUOVO ENDPOINT - Pubblico e compatibile con frontend
    @GetMapping("/is-following/{followedId}")
    public Map<String, Boolean> isFollowing(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long followedId) {

        // Se l'utente NON è autenticato, ritorna false
        if (userDetails == null) {
            Map<String, Boolean> response = new HashMap<>();
            response.put("isFollowing", false);
            return response;
        }

        User currentUser = userDetails.getUser();
        boolean isFollowing = followService.isFollowing(currentUser.getId(), followedId);

        Map<String, Boolean> response = new HashMap<>();
        response.put("isFollowing", isFollowing);
        return response;
    }
}