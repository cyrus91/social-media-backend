package com.social.backend.components.follow.service.impl;

import com.social.backend.common.exception.ConflictException;
import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.follow.dto.FollowRequest;
import com.social.backend.components.follow.dto.FollowResponse;
import com.social.backend.components.follow.entity.Follow;
import com.social.backend.components.follow.repository.FollowRepository;
import com.social.backend.components.follow.service.FollowService;
import com.social.backend.components.notification.enums.NotificationType;
import com.social.backend.components.notification.repository.NotificationRepository;
import com.social.backend.components.notification.service.NotificationService;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl implements FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    public FollowServiceImpl(FollowRepository followRepository,
                             UserRepository userRepository,
                             NotificationService notificationService,
                             NotificationRepository notificationRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public FollowResponse create(Long followerId, FollowRequest request) {
        // Impedisci di seguire sé stessi
        if (followerId.equals(request.getFollowedId())) {
            throw new ConflictException("Non puoi seguire te stesso");
        }

        // Trova il follower
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + followerId));

        // Trova l'utente da seguire
        User followed = userRepository.findById(request.getFollowedId())
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + request.getFollowedId()));

        // Verifica che non stia già seguendo
        if (followRepository.existsByFollowerIdAndFollowedId(followerId, request.getFollowedId())) {
            throw new ConflictException("Stai già seguendo questo utente");
        }

        // Crea il follow
        Follow follow = Follow.builder()
                .followerId(followerId)
                .followedId(request.getFollowedId())
                .follower(follower)
                .followed(followed)
                .createdAt(LocalDateTime.now())
                .build();

        // Salva
        Follow savedFollow = followRepository.save(follow);

        // Log per debug
        Long followerCount = followRepository.countByFollowedId(request.getFollowedId());
        System.out.println("✅ Follow salvato! User " + request.getFollowedId() + " ora ha " + followerCount + " followers");

        // Invia notifica all'utente seguito
        String message = follower.getUsername() + " ha iniziato a seguirti";
        notificationService.createNotification(
                request.getFollowedId(),
                followerId,
                NotificationType.FOLLOW,
                null,
                null,
                message
        );

        return mapToResponse(savedFollow);
    }

    @Override
    @Transactional
    public void delete(Long followerId, Long followedId) {
        Follow follow = followRepository.findByFollowerIdAndFollowedId(followerId, followedId)
                .orElseThrow(() -> new ResourceNotFoundException("Follow non trovato"));

        followRepository.delete(follow);

        // Elimina la notifica FOLLOW associata con query diretta (no findAll!)
        notificationRepository.deleteByRecipientIdAndActorIdAndType(
                followedId, followerId, NotificationType.FOLLOW
        );
    }

    @Override
    public List<FollowResponse> listFollowers(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Utente non trovato con ID: " + userId);
        }
        return followRepository.findByFollowedId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<FollowResponse> listFollowing(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Utente non trovato con ID: " + userId);
        }
        return followRepository.findByFollowerId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public int countFollowers(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Utente non trovato con ID: " + userId);
        }
        return (int) followRepository.countByFollowedId(userId);
    }

    @Override
    public int countFollowing(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Utente non trovato con ID: " + userId);
        }
        return (int) followRepository.countByFollowerId(userId);
    }

    @Override
    public boolean isFollowing(Long followerId, Long followedId) {
        return followRepository.existsByFollowerIdAndFollowedId(followerId, followedId);
    }

    private FollowResponse mapToResponse(Follow follow) {
        FollowResponse response = new FollowResponse();
        response.setFollowerId(follow.getFollowerId());
        response.setFollowerUsername(follow.getFollower().getUsername());
        response.setFollowerAvatarUrl(follow.getFollower().getAvatarUrl());
        response.setFollowedId(follow.getFollowedId());
        response.setFollowedUsername(follow.getFollowed().getUsername());
        response.setFollowedAvatarUrl(follow.getFollowed().getAvatarUrl());
        response.setCreatedAt(follow.getCreatedAt());
        return response;
    }
}