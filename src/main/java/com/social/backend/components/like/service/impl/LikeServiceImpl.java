package com.social.backend.components.like.service.impl;

import com.social.backend.components.comment.repository.CommentRepository;
import com.social.backend.components.like.dto.LikeRequest;
import com.social.backend.components.like.dto.LikeResponse;
import com.social.backend.components.like.entity.Like;
import com.social.backend.components.like.entity.LikeId;
import com.social.backend.components.like.repository.LikeRepository;
import com.social.backend.components.like.service.LikeService;
import com.social.backend.components.notification.enums.NotificationType;
import com.social.backend.components.notification.service.NotificationService;
import com.social.backend.components.post.dto.PostResponse;
import com.social.backend.components.post.entity.Post;
import com.social.backend.components.post.entity.PostImage;
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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LikeServiceImpl implements LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CommentRepository commentRepository;

    public LikeServiceImpl(LikeRepository likeRepository,
                           PostRepository postRepository,
                           UserRepository userRepository,
                           NotificationService notificationService,
                           CommentRepository commentRepository) {
        this.likeRepository = likeRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.commentRepository = commentRepository;
    }

    @Override
    public LikeResponse create(Long userId, LikeRequest request) {
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato con ID: " + request.getPostId()));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + userId));

        LikeId likeId = new LikeId(userId, request.getPostId());
        if (likeRepository.existsById(likeId)) {
            throw new ConflictException("Hai già messo like a questo post");
        }

        Like like = Like.builder()
                .userId(userId)
                .postId(request.getPostId())
                .user(user)
                .post(post)
                .createdAt(LocalDateTime.now())
                .build();

        Like savedLike = likeRepository.save(like);

        String message = user.getUsername() + " ha messo like al tuo post";
        notificationService.createNotification(
                post.getAuthor().getId(),
                userId,
                NotificationType.LIKE,
                request.getPostId(),
                null,
                message
        );

        return mapToResponse(savedLike);
    }

    @Override
    public void delete(Long userId, Long postId) {
        LikeId likeId = new LikeId(userId, postId);
        Like like = likeRepository.findById(likeId)
                .orElseThrow(() -> new ResourceNotFoundException("Like non trovato"));

        if (!like.getUserId().equals(userId)) {
            throw new ForbiddenException("Non puoi rimuovere il like di un altro utente");
        }

        likeRepository.delete(like);
    }

    @Override
    public List<LikeResponse> listByPost(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post non trovato con ID: " + postId);
        }
        return likeRepository.findByPostId(postId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public int countByPost(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post non trovato con ID: " + postId);
        }
        return likeRepository.countByPostId(postId);
    }

    @Override
    public boolean hasUserLikedPost(Long userId, Long postId) {
        return likeRepository.existsById(new LikeId(userId, postId));
    }

    @Override
    public Page<LikeResponse> listByPost(Long postId, Pageable pageable) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post non trovato con ID: " + postId);
        }
        return likeRepository.findByPostId(postId, pageable).map(this::mapToResponse);
    }

    @Override
    public Page<PostResponse> getLikedPostsByUser(Long userId, Long currentUserId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Utente non trovato con ID: " + userId);
        }
        return likeRepository.findLikedPostsByUserId(userId, pageable)
                .map(post -> mapPostToResponse(post, currentUserId));
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

    private PostResponse mapPostToResponse(Post post, Long currentUserId) {
        int likeCount = likeRepository.countByPostId(post.getId());
        int commentCount = commentRepository.countByPostId(post.getId());
        boolean liked = currentUserId != null &&
                likeRepository.existsByUserIdAndPostId(currentUserId, post.getId());

        List<String> imageUrls = post.getImages().stream()
                .sorted(Comparator.comparingInt(PostImage::getDisplayOrder))
                .map(PostImage::getImageUrl)
                .toList();

        if (imageUrls.isEmpty() && post.getImageUrl() != null) {
            imageUrls = List.of(post.getImageUrl());
        }

        return PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .imageUrls(imageUrls)
                .imageUrl(imageUrls.isEmpty() ? null : imageUrls.get(0))
                .authorId(post.getAuthor().getId())
                .authorUsername(post.getAuthor().getUsername())
                .authorAvatarUrl(post.getAuthor().getAvatarUrl())
                .createdAt(post.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .updatedAt(post.getUpdatedAt() != null ?
                        post.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .likeCount(likeCount)
                .commentCount(commentCount)
                .liked(liked)
                .build();
    }
}