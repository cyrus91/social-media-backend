package com.social.backend.components.bookmark.service.impl;

import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.bookmark.entity.Bookmark;
import com.social.backend.components.bookmark.repository.BookmarkRepository;
import com.social.backend.components.bookmark.service.BookmarkService;
import com.social.backend.components.like.repository.LikeRepository;
import com.social.backend.components.comment.repository.CommentRepository;
import com.social.backend.components.poll.dto.PollOptionResponse;
import com.social.backend.components.poll.dto.PollResponse;
import com.social.backend.components.poll.repository.PollRepository;
import com.social.backend.components.poll.repository.PollVoteRepository;
import com.social.backend.components.post.dto.PostImageDto;
import com.social.backend.components.post.dto.PostResponse;
import com.social.backend.components.post.entity.Post;
import com.social.backend.components.post.entity.PostImage;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkServiceImpl implements BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;

    @Override
    @Transactional
    public boolean toggle(Long userId, Long postId) {
        if (bookmarkRepository.existsByUserIdAndPostId(userId, postId)) {
            bookmarkRepository.deleteByUserIdAndPostId(userId, postId);
            return false; // rimosso
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato"));
        bookmarkRepository.save(Bookmark.builder().user(user).post(post).build());
        return true; // aggiunto
    }

    @Override
    public boolean isBookmarked(Long userId, Long postId) {
        return bookmarkRepository.existsByUserIdAndPostId(userId, postId);
    }

    @Override
    public Page<PostResponse> getUserBookmarks(Long userId, Long currentUserId, int page, int size) {
        return bookmarkRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(b -> mapToResponse(b.getPost(), currentUserId));
    }

    private PostResponse mapToResponse(Post post, Long currentUserId) {
        int likeCount = likeRepository.countByPostId(post.getId());
        int commentCount = commentRepository.countByPostId(post.getId());
        boolean liked = currentUserId != null &&
                likeRepository.existsByUserIdAndPostId(currentUserId, post.getId());
        boolean bookmarked = currentUserId != null &&
                bookmarkRepository.existsByUserIdAndPostId(currentUserId, post.getId());

        List<PostImageDto> imageDtos = post.getImages().stream()
                .sorted(Comparator.comparingInt(PostImage::getDisplayOrder))
                .map(img -> PostImageDto.builder()
                        .id(img.getId())
                        .imageUrl(img.getImageUrl())
                        .displayOrder(img.getDisplayOrder())
                        .build())
                .toList();

        List<String> imageUrls = imageDtos.stream().map(PostImageDto::getImageUrl).toList();
        if (imageUrls.isEmpty() && post.getImageUrl() != null) {
            imageUrls = List.of(post.getImageUrl());
        }

        // Carica sondaggio se presente
        PollResponse pollData = null;
        try {
            pollData = pollRepository.findByPostIdWithOptions(post.getId()).map(p -> {
                long total = p.getOptions().stream().mapToLong(o -> o.getVoteCount() != null ? o.getVoteCount() : 0L).sum();
                Long votedOptionId = currentUserId != null
                        ? pollVoteRepository.findVotedOptionId(p.getId(), currentUserId).orElse(null)
                        : null;
                return PollResponse.builder()
                        .id(p.getId())
                        .question(p.getQuestion())
                        .options(p.getOptions().stream().map(o -> PollOptionResponse.builder()
                                .id(o.getId()).text(o.getText())
                                .voteCount(o.getVoteCount() != null ? o.getVoteCount() : 0L)
                                .percentage(total > 0 ? (o.getVoteCount() != null ? o.getVoteCount() : 0L) * 100.0 / total : 0)
                                .build()).toList())
                        .totalVotes(total)
                        .votedOptionId(votedOptionId)
                        .expired(p.isExpired())
                        .expiresAt(p.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant())
                        .build();
            }).orElse(null);
        } catch (Exception e) {
            log.warn("Errore caricamento poll per post bookmark {}: {}", post.getId(), e.getMessage());
        }

        return PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .images(imageDtos)
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
                .viewCount(post.getViewCount())
                .liked(liked)
                .bookmarked(bookmarked)
                .poll(pollData)
                .build();
    }
}