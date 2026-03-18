package com.social.backend.components.like.service.impl;

import com.social.backend.components.like.dto.LikeRequest;
import com.social.backend.components.like.dto.LikeResponse;
import com.social.backend.components.like.entity.Like;
import com.social.backend.components.like.entity.LikeId;
import com.social.backend.components.like.repository.LikeRepository;
import com.social.backend.components.like.service.LikeService;
import com.social.backend.components.notification.enums.NotificationType;
import com.social.backend.components.notification.service.NotificationService;
import com.social.backend.components.post.entity.Post;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import com.social.backend.common.exception.ConflictException;
import com.social.backend.common.exception.ForbiddenException;
import com.social.backend.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LikeServiceImpl implements LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public LikeServiceImpl(LikeRepository likeRepository,
                           PostRepository postRepository,
                           UserRepository userRepository,
                           NotificationService notificationService) {
        this.likeRepository = likeRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    public LikeResponse create(Long userId, LikeRequest request) {
        // Trova il post
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato con ID: " + request.getPostId()));

        // Trova l'utente
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + userId));

        // Verifica che l'utente non abbia già messo like (la chiave composta impedisce già duplicati)
        LikeId likeId = new LikeId(userId, request.getPostId());
        if (likeRepository.existsById(likeId)) {
            throw new ConflictException("Hai già messo like a questo post");
        }

        // Crea il like
        Like like = Like.builder()
                .userId(userId)
                .postId(request.getPostId())
                .user(user)
                .post(post)
                .createdAt(LocalDateTime.now())
                .build();

        // Salva
        Like savedLike = likeRepository.save(like);

        //  Crea notifica
        String message = user.getUsername() + " ha messo like al tuo post";
        notificationService.createNotification(
                post.getAuthor().getId(),  // Destinatario: autore del post
                userId,                     // Attore: chi ha messo like
                NotificationType.LIKE,      // Tipo
                request.getPostId(),        // Post relativo
                null,                       // Nessun commento
                message                     // Messaggio
        );

        return mapToResponse(savedLike);
    }

    @Override
    public void delete(Long userId, Long postId) {
        // Crea la chiave composta
        LikeId likeId = new LikeId(userId, postId);

        // Trova il like
        Like like = likeRepository.findById(likeId)
                .orElseThrow(() -> new ResourceNotFoundException("Like non trovato"));

        // La verifica di proprietà è implicita (userId fa parte della chiave)
        // Ma la facciamo comunque per sicurezza
        if (!like.getUserId().equals(userId)) {
            throw new ForbiddenException("Non puoi rimuovere il like di un altro utente");
        }

        likeRepository.delete(like);
    }

    @Override
    public List<LikeResponse> listByPost(Long postId) {
        // Verifica che il post esista
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post non trovato con ID: " + postId);
        }

        List<Like> likes = likeRepository.findByPostId(postId);
        return likes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public int countByPost(Long postId) {
        // Verifica che il post esista
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post non trovato con ID: " + postId);
        }

        return likeRepository.countByPostId(postId);
    }

    @Override
    public boolean hasUserLikedPost(Long userId, Long postId) {
        LikeId likeId = new LikeId(userId, postId);
        return likeRepository.existsById(likeId);
    }

    @Override
    public Page<LikeResponse> listByPost(Long postId, Pageable pageable) {
        // Verifica che il post esista
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post non trovato con ID: " + postId);
        }

        Page<Like> likes = likeRepository.findByPostId(postId, pageable);
        return likes.map(this::mapToResponse);
    }

    private LikeResponse mapToResponse(Like like) {
        return LikeResponse.builder()
                .userId(like.getUserId())
                .username(like.getUser().getUsername())
                .avatarUrl(like.getUser().getAvatarUrl())
                .postId(like.getPostId())
                .createdAt(like.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .build();
    }
}