package com.social.backend.components.comment.service.impl;

import com.social.backend.components.comment.dto.CommentResponse;
import com.social.backend.components.comment.dto.CreateCommentRequest;
import com.social.backend.components.comment.dto.UpdateCommentRequest;
import com.social.backend.components.comment.entity.Comment;
import com.social.backend.components.comment.entity.CommentReaction;
import com.social.backend.components.comment.repository.CommentReactionRepository;
import com.social.backend.components.comment.repository.CommentRepository;
import com.social.backend.components.comment.service.CommentService;
import com.social.backend.components.mention.MentionService;
import com.social.backend.components.notification.enums.NotificationType;
import com.social.backend.components.notification.repository.NotificationRepository;
import com.social.backend.components.notification.service.NotificationService;
import com.social.backend.components.post.entity.Post;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import com.social.backend.common.exception.ForbiddenException;
import com.social.backend.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl implements CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CommentReactionRepository reactionRepository;
    private final NotificationRepository notificationRepository;
    private final MentionService mentionService;

    public CommentServiceImpl(CommentRepository commentRepository,
                              PostRepository postRepository,
                              UserRepository userRepository,
                              NotificationService notificationService,
                              CommentReactionRepository reactionRepository,
                              NotificationRepository notificationRepository,
                              MentionService mentionService) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.reactionRepository = reactionRepository;
        this.notificationRepository = notificationRepository;
        this.mentionService = mentionService;
    }

    @Override
    public CommentResponse create(Long authorId, CreateCommentRequest request) {
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato con ID: " + request.getPostId()));
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + authorId));

        // Valida: deve esserci almeno content o imageUrl
        if ((request.getContent() == null || request.getContent().isBlank()) && request.getImageUrl() == null) {
            throw new IllegalArgumentException("Il commento deve avere testo o immagine");
        }

        Comment comment = new Comment();
        comment.setContent(request.getContent() != null && !request.getContent().isBlank()
                ? request.getContent() : null);
        comment.setImageUrl(request.getImageUrl());
        comment.setPost(post);
        comment.setAuthor(author);

        // Risposta a un commento esistente
        if (request.getParentId() != null) {
            commentRepository.findById(request.getParentId())
                    .ifPresent(comment::setParent);
        }

        Comment saved = commentRepository.save(comment);

        if (request.getParentId() != null) {
            // Risposta a un commento → notifica all'autore del commento padre
            commentRepository.findById(request.getParentId()).ifPresent(parent -> {
                if (!parent.getAuthor().getId().equals(authorId)) {
                    notificationService.createNotification(
                            parent.getAuthor().getId(), authorId,
                            NotificationType.COMMENT, request.getPostId(),
                            saved.getId(), author.getUsername() + " ha risposto al tuo commento");
                }
            });
        } else {
            // Commento normale → notifica all'autore del post
            if (!post.getAuthor().getId().equals(authorId)) {
                notificationService.createNotification(
                        post.getAuthor().getId(), authorId,
                        NotificationType.COMMENT, request.getPostId(),
                        saved.getId(), author.getUsername() + " ha commentato il tuo post");
            }
        }

        // Processa mention @username nel commento
        mentionService.processMentions(request.getContent(), authorId,
                request.getPostId(), saved.getId(), author.getUsername());

        return mapToResponse(saved, authorId);
    }

    @Override
    public CommentResponse getById(Long id) {
        Comment c = commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commento non trovato con ID: " + id));
        return mapToResponse(c, null);
    }

    @Override
    public List<CommentResponse> listByPost(Long postId) {
        if (!postRepository.existsById(postId))
            throw new ResourceNotFoundException("Post non trovato con ID: " + postId);
        return commentRepository.findByPostId(postId).stream()
                .map(c -> mapToResponse(c, null)).collect(Collectors.toList());
    }

    // Versione paginata con userId — usata dal controller autenticato
    @Override
    public Page<CommentResponse> listByPost(Long postId, Pageable pageable, Long userId) {
        if (!postRepository.existsById(postId))
            throw new ResourceNotFoundException("Post non trovato con ID: " + postId);
        return commentRepository.findByPostId(postId, pageable).map(c -> mapToResponse(c, userId));
    }

    // Versione con userId per mostrare myReaction
    public List<CommentResponse> listByPost(Long postId, Long userId) {
        if (!postRepository.existsById(postId))
            throw new ResourceNotFoundException("Post non trovato con ID: " + postId);
        return commentRepository.findByPostId(postId).stream()
                .map(c -> mapToResponse(c, userId)).collect(Collectors.toList());
    }

    @Override
    public Page<CommentResponse> listByPost(Long postId, Pageable pageable) {
        if (!postRepository.existsById(postId))
            throw new ResourceNotFoundException("Post non trovato con ID: " + postId);
        return commentRepository.findByPostId(postId, pageable).map(c -> mapToResponse(c, null));
    }

    @Override
    public CommentResponse update(Long currentUserId, Long commentId, UpdateCommentRequest request) {
        Comment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Commento non trovato con ID: " + commentId));
        if (!c.getAuthor().getId().equals(currentUserId))
            throw new ForbiddenException("Non puoi modificare un commento di un altro utente");
        c.setContent(request.getContent());
        return mapToResponse(commentRepository.save(c), currentUserId);
    }

    @Override
    public void delete(Long currentUserId, Long commentId) {
        Comment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Commento non trovato con ID: " + commentId));
        if (!c.getAuthor().getId().equals(currentUserId))
            throw new ForbiddenException("Non puoi eliminare un commento di un altro utente");
        commentRepository.delete(c);
    }

    @Override
    @Transactional
    public CommentResponse toggleReaction(Long commentId, Long userId, String emoji) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Commento non trovato"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));

        // Carica postId con query diretta — nessun lazy loading coinvolto
        Long postId = commentRepository.findPostIdById(commentId).orElse(null);
        Long authorId = comment.getAuthor().getId();
        log.info("🔔 toggleReaction: commentId={} userId={} emoji={} authorId={} postId={}", commentId, userId, emoji, authorId, postId);

        Optional<CommentReaction> existing = reactionRepository.findByCommentIdAndUserId(commentId, userId);
        boolean sendNotification = false;

        if (existing.isPresent()) {
            if (existing.get().getEmoji().equals(emoji)) {
                // Stesso emoji → toggle off
                reactionRepository.deleteByCommentIdAndUserId(commentId, userId);
            } else {
                // Emoji diversa → aggiorna e notifica
                reactionRepository.updateEmoji(commentId, userId, emoji);
                sendNotification = true;
            }
        } else {
            // Nuova reazione
            reactionRepository.save(CommentReaction.builder()
                    .comment(comment).user(user).emoji(emoji).build());
            sendNotification = true;
        }

        // Notifica — elimina la precedente per questo commento+utente per evitare il duplicate check
        log.info("🔔 sendNotification={} authorId={} userId={} sameUser={} postId={}", sendNotification, authorId, userId, authorId.equals(userId), postId);
        if (sendNotification && !authorId.equals(userId) && postId != null) {
            notificationRepository.deleteByRecipientActorTypeComment(
                    authorId, userId, NotificationType.REACTION, commentId);
            notificationService.createNotification(
                    authorId, userId,
                    NotificationType.REACTION, postId,
                    commentId, user.getUsername() + " ha reagito al tuo commento con " + emoji);
        }

        return mapToResponse(commentRepository.findById(commentId).get(), userId);
    }

    @Override
    @Transactional
    public void deleteImage(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Commento non trovato"));
        if (!comment.getAuthor().getId().equals(userId))
            throw new ForbiddenException("Non autorizzato");
        comment.setImageUrl(null);
        commentRepository.save(comment);
    }

    private CommentResponse mapToResponse(Comment comment, Long currentUserId) {
        List<CommentReaction> reactions = reactionRepository.findByCommentId(comment.getId());
        Map<String, Long> reactionMap = reactions.stream()
                .collect(Collectors.groupingBy(CommentReaction::getEmoji, Collectors.counting()));
        String myReaction = currentUserId == null ? null : reactions.stream()
                .filter(r -> r.getUser().getId().equals(currentUserId))
                .map(CommentReaction::getEmoji)
                .findFirst().orElse(null);

        // Carica risposte (solo per commenti principali — no ricorsione infinita)
        List<CommentResponse> replies = comment.getParent() == null
                ? commentRepository.findReplies(comment.getId()).stream()
                .map(r -> mapToResponse(r, currentUserId))
                .collect(Collectors.toList())
                : null;

        return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .postId(comment.getPost().getId())
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .authorId(comment.getAuthor().getId())
                .authorUsername(comment.getAuthor().getUsername())
                .authorAvatarUrl(comment.getAuthor().getAvatarUrl())
                .imageUrl(comment.getImageUrl())
                .createdAt(comment.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .updatedAt(comment.getUpdatedAt() != null
                        ? comment.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .reactions(reactionMap)
                .myReaction(myReaction)
                .replies(replies)
                .build();
    }
}