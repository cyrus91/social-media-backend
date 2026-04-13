package com.social.backend.components.admin.service.impl;

import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.admin.dto.AdminPostResponse;
import com.social.backend.components.admin.dto.AdminStatsResponse;
import com.social.backend.components.admin.service.AdminService;
import com.social.backend.components.comment.repository.CommentRepository;
import com.social.backend.components.follow.repository.FollowRepository;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.post.service.PostService;
import com.social.backend.components.user.dto.UserResponse;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.enums.Role;
import com.social.backend.components.user.repository.UserRepository;
import com.social.backend.components.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final FollowRepository followRepository;
    private final UserService userService;
    private final PostService postService;

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        return AdminStatsResponse.builder()
                .totalUsers(userRepository.count())
                .totalPosts(postRepository.count())
                .totalComments(commentRepository.count())
                .bannedUsers(userRepository.countByBanned(true))
                .adminUsers(userRepository.countByRole(Role.ADMIN))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(int page, int size) {
        return userRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::mapUserToResponse);
    }

    @Override
    @Transactional
    public Map<String, Object> toggleBan(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));
        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Non puoi bannare un admin");
        }
        user.setBanned(!user.isBanned());
        userRepository.save(user);
        log.info("User {} ban status changed to {}", user.getUsername(), user.isBanned());
        return Map.of("banned", user.isBanned(), "username", user.getUsername());
    }

    @Override
    @Transactional
    public Map<String, Object> changeRole(Long userId, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));
        user.setRole(role);
        userRepository.save(user);
        log.info("User {} role changed to {}", user.getUsername(), role);
        return Map.of("role", user.getRole(), "username", user.getUsername());
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        userService.deleteUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminPostResponse> getAllPosts(int page, int size) {
        return postRepository
                .findAllWithAuthor(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(post -> AdminPostResponse.builder()
                        .id(post.getId())
                        .content(post.getContent())
                        .authorUsername(post.getAuthor().getUsername())
                        .authorId(post.getAuthor().getId())
                        .createdAt(post.getCreatedAt())
                        .commentCount(commentRepository.countByPostId(post.getId()))
                        .build());
    }

    @Override
    @Transactional
    public void deletePost(Long postId) {
        var post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato"));
        postService.delete(post.getAuthor().getId(), postId);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new ResourceNotFoundException("Commento non trovato");
        }
        commentRepository.deleteById(commentId);
    }

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