package com.social.backend.components.admin.controller;

import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.comment.repository.CommentRepository;
import com.social.backend.components.follow.repository.FollowRepository;
import com.social.backend.components.post.entity.Post;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.post.service.PostService;
import com.social.backend.components.user.dto.UserResponse;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import com.social.backend.components.user.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final FollowRepository followRepository;
    private final UserService userService;
    private final PostService postService;

    public AdminController(UserRepository userRepository,
                           PostRepository postRepository,
                           CommentRepository commentRepository,
                           FollowRepository followRepository,
                           UserService userService,
                           PostService postService) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.followRepository = followRepository;
        this.userService = userService;
        this.postService = postService;
    }

    // ============================================
    // STATISTICHE GENERALI
    // ============================================

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalPosts", postRepository.count());
        stats.put("totalComments", commentRepository.count());
        stats.put("bannedUsers", userRepository.countByBanned(true));
        stats.put("adminUsers", userRepository.countByRole("ADMIN"));
        return stats;
    }

    // ============================================
    // GESTIONE UTENTI
    // ============================================

    @GetMapping("/users")
    public List<UserResponse> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return userRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent().stream().map(this::mapUserToResponse).collect(Collectors.toList());
    }

    @PutMapping("/users/{userId}/ban")
    public Map<String, Object> banUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));
        if ("ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("Non puoi bannare un admin");
        }
        user.setBanned(!user.isBanned());
        userRepository.save(user);
        Map<String, Object> response = new HashMap<>();
        response.put("banned", user.isBanned());
        response.put("username", user.getUsername());
        return response;
    }

    @PutMapping("/users/{userId}/role")
    public Map<String, Object> changeRole(
            @PathVariable Long userId,
            @RequestParam String role) {
        if (!role.equals("USER") && !role.equals("ADMIN")) {
            throw new IllegalArgumentException("Ruolo non valido. Usa USER o ADMIN");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));
        user.setRole(role);
        userRepository.save(user);
        Map<String, Object> response = new HashMap<>();
        response.put("role", user.getRole());
        response.put("username", user.getUsername());
        return response;
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
    }

    // ============================================
    // GESTIONE POST
    // ============================================

    @GetMapping("/posts")
    public List<Map<String, Object>> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return postRepository.findAllWithAuthor(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent().stream().map(post -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", post.getId());
            map.put("content", post.getContent());
            map.put("authorUsername", post.getAuthor().getUsername());
            map.put("authorId", post.getAuthor().getId());
            map.put("createdAt", post.getCreatedAt());
            map.put("commentCount", commentRepository.countByPostId(post.getId()));
            return map;
        }).collect(Collectors.toList());
    }

    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deletePost(@PathVariable Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato"));
        postService.delete(post.getAuthor().getId(), postId);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteComment(@PathVariable Long commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new ResourceNotFoundException("Commento non trovato");
        }
        commentRepository.deleteById(commentId);
    }

    // ============================================
    // MAPPER
    // ============================================

    private UserResponse mapUserToResponse(User user) {
        Long followerCount = followRepository.countByFollowedId(user.getId());
        Long followingCount = followRepository.countByFollowerId(user.getId());
        Integer postCount = postRepository.countByAuthorId(user.getId());
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .bio(user.getBio())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .followerCount(followerCount != null ? followerCount.intValue() : 0)
                .followingCount(followingCount != null ? followingCount.intValue() : 0)
                .postCount(postCount != null ? postCount : 0)
                .role(user.getRole())
                .banned(user.isBanned())
                .emailVerified(user.isEmailVerified())
                .build();
    }
}