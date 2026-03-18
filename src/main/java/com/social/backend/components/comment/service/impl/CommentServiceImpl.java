package com.social.backend.components.comment.service.impl;

import com.social.backend.components.comment.dto.CommentResponse;
import com.social.backend.components.comment.dto.CreateCommentRequest;
import com.social.backend.components.comment.dto.UpdateCommentRequest;
import com.social.backend.components.comment.entity.Comment;
import com.social.backend.components.comment.repository.CommentRepository;
import com.social.backend.components.comment.service.CommentService;
import com.social.backend.components.notification.enums.NotificationType;
import com.social.backend.components.notification.service.NotificationService;
import com.social.backend.components.post.entity.Post;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import com.social.backend.common.exception.ForbiddenException;
import com.social.backend.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public CommentServiceImpl(CommentRepository commentRepository,
                              PostRepository postRepository,
                              UserRepository userRepository,
                              NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    public CommentResponse create(Long authorId, CreateCommentRequest request) {
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato con ID: " + request.getPostId()));

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + authorId));

        Comment comment = new Comment();
        comment.setContent(request.getContent());
        comment.setPost(post);
        comment.setAuthor(author);

        Comment savedComment = commentRepository.save(comment);

        // AGGIUNGI NOTIFICA
        String message = author.getUsername() + " ha commentato il tuo post";
        notificationService.createNotification(
                post.getAuthor().getId(),   // Destinatario: autore del post
                authorId,                    // Attore: chi ha commentato
                NotificationType.COMMENT,    // Tipo
                request.getPostId(),         // Post relativo
                savedComment.getId(),        // Commento relativo
                message                      // Messaggio
        );

        return mapToResponse(savedComment);
    }

    @Override
    public CommentResponse getById(Long id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commento non trovato con ID: " + id));
        return mapToResponse(comment);
    }

    @Override
    public List<CommentResponse> listByPost(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post non trovato con ID: " + postId);
        }

        List<Comment> comments = commentRepository.findByPostId(postId);
        return comments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<CommentResponse> listByPost(Long postId, Pageable pageable) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post non trovato con ID: " + postId);
        }

        Page<Comment> comments = commentRepository.findByPostId(postId, pageable);
        return comments.map(this::mapToResponse);
    }

    @Override
    public CommentResponse update(Long currentUserId, Long commentId, UpdateCommentRequest request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Commento non trovato con ID: " + commentId));

        if (!comment.getAuthor().getId().equals(currentUserId)) {
            throw new ForbiddenException("Non puoi modificare un commento di un altro utente");
        }

        comment.setContent(request.getContent());
        Comment updatedComment = commentRepository.save(comment);
        return mapToResponse(updatedComment);
    }

    @Override
    public void delete(Long currentUserId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Commento non trovato con ID: " + commentId));

        if (!comment.getAuthor().getId().equals(currentUserId)) {
            throw new ForbiddenException("Non puoi eliminare un commento di un altro utente");
        }

        commentRepository.delete(comment);
    }

    private CommentResponse mapToResponse(Comment comment) {
        CommentResponse response = new CommentResponse();
        response.setId(comment.getId());
        response.setContent(comment.getContent());
        response.setPostId(comment.getPost().getId());
        response.setAuthorId(comment.getAuthor().getId());
        response.setAuthorUsername(comment.getAuthor().getUsername());

        response.setCreatedAt(comment.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant());

        if (comment.getUpdatedAt() != null) {
            response.setUpdatedAt(comment.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant());
        }
        return response;
    }
}