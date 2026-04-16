package com.social.backend.components.comment.service;

import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.comment.dto.CommentResponse;
import com.social.backend.components.comment.dto.CreateCommentRequest;
import com.social.backend.components.comment.entity.Comment;
import com.social.backend.components.comment.repository.CommentReactionRepository;
import com.social.backend.components.comment.repository.CommentRepository;
import com.social.backend.components.comment.service.impl.CommentServiceImpl;
import com.social.backend.components.mention.MentionService;
import com.social.backend.components.notification.enums.NotificationType;
import com.social.backend.components.notification.repository.NotificationRepository;
import com.social.backend.components.notification.service.NotificationService;
import com.social.backend.components.post.entity.Post;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService Unit Tests")
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private CommentReactionRepository reactionRepository;
    @Mock private MentionService mentionService;

    @InjectMocks
    private CommentServiceImpl commentService;

    private User postAuthor;
    private User commenter;
    private Post post;
    private Comment rootComment;
    private Comment replyComment;

    @BeforeEach
    void setUp() {
        postAuthor = User.builder().id(1L).username("postAuthor").build();
        commenter  = User.builder().id(2L).username("cirodattilo91").build();

        post = Post.builder().id(10L).author(postAuthor)
                .content("Post di test").createdAt(LocalDateTime.now()).build();

        rootComment = Comment.builder().id(100L).author(commenter)
                .post(post).content("Commento root").createdAt(LocalDateTime.now()).build();

        replyComment = Comment.builder().id(101L).author(postAuthor)
                .post(post).content("Risposta al commento").parent(rootComment).createdAt(LocalDateTime.now()).build();
    }

    @Nested
    @DisplayName("create — commento root")
    class CreateRootComment {

        @Test
        @DisplayName("Dovrebbe creare commento e notificare l'autore del post")
        void shouldCreateCommentAndNotifyPostAuthor() {
            CreateCommentRequest request = new CreateCommentRequest();
            request.setPostId(10L);
            request.setContent("Bel post!");

            when(postRepository.findById(10L)).thenReturn(Optional.of(post));
            when(userRepository.findById(2L)).thenReturn(Optional.of(commenter));
            when(commentRepository.save(any())).thenReturn(rootComment);
            when(reactionRepository.findByCommentId(anyLong())).thenReturn(Collections.emptyList());
            when(commentRepository.findReplies(anyLong())).thenReturn(Collections.emptyList());

            CommentResponse result = commentService.create(2L, request);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEqualTo("Commento root");

            // Deve notificare l'autore del post (id=1), non se stesso
            verify(notificationService).createNotification(
                    eq(1L), eq(2L), eq(NotificationType.COMMENT), eq(10L), any(), anyString());
        }

        @Test
        @DisplayName("Non deve notificare se l'autore commenta il proprio post")
        void shouldNotNotifyWhenAuthorCommentsOwnPost() {
            CreateCommentRequest request = new CreateCommentRequest();
            request.setPostId(10L);
            request.setContent("Il mio commento");

            Comment selfComment = Comment.builder().id(102L).author(postAuthor)
                    .post(post).content("Il mio commento").createdAt(LocalDateTime.now()).build();

            when(postRepository.findById(10L)).thenReturn(Optional.of(post));
            when(userRepository.findById(1L)).thenReturn(Optional.of(postAuthor));
            when(commentRepository.save(any())).thenReturn(selfComment);
            when(reactionRepository.findByCommentId(anyLong())).thenReturn(Collections.emptyList());
            when(commentRepository.findReplies(anyLong())).thenReturn(Collections.emptyList());

            commentService.create(1L, request); // authorId == postAuthor.id

            verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Dovrebbe lanciare eccezione se il post non esiste")
        void shouldThrowIfPostNotFound() {
            CreateCommentRequest request = new CreateCommentRequest();
            request.setPostId(999L);
            request.setContent("Commento");

            when(postRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commentService.create(2L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Dovrebbe lanciare eccezione se il commento è vuoto e non ha immagine")
        void shouldThrowIfContentAndImageBothMissing() {
            CreateCommentRequest request = new CreateCommentRequest();
            request.setPostId(10L);
            request.setContent("   "); // solo spazi

            when(postRepository.findById(10L)).thenReturn(Optional.of(post));
            when(userRepository.findById(2L)).thenReturn(Optional.of(commenter));

            assertThatThrownBy(() -> commentService.create(2L, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("create — risposta a commento")
    class CreateReply {

        @Test
        @DisplayName("Dovrebbe notificare l'autore del commento padre (non il post author)")
        void shouldNotifyParentCommentAuthorOnReply() {
            CreateCommentRequest request = new CreateCommentRequest();
            request.setPostId(10L);
            request.setContent("Risposta!");
            request.setParentId(100L); // rootComment.id (autore: commenter id=2)

            Comment reply = Comment.builder().id(103L).author(postAuthor)
                    .post(post).content("Risposta!").parent(rootComment).createdAt(LocalDateTime.now()).build();

            when(postRepository.findById(10L)).thenReturn(Optional.of(post));
            when(userRepository.findById(1L)).thenReturn(Optional.of(postAuthor));
            when(commentRepository.save(any())).thenReturn(reply);
            when(commentRepository.findById(100L)).thenReturn(Optional.of(rootComment));
            when(reactionRepository.findByCommentId(anyLong())).thenReturn(Collections.emptyList());

            commentService.create(1L, request); // postAuthor risponde a rootComment (di commenter)

            // Deve notificare commenter (id=2), autore del commento padre
            verify(notificationService).createNotification(
                    eq(2L), eq(1L), eq(NotificationType.COMMENT), eq(10L), any(), anyString());
        }

        @Test
        @DisplayName("Con replyToCommentId deve notificare l'autore del commento specifico")
        void shouldNotifySpecificCommentAuthorWithReplyToCommentId() {
            CreateCommentRequest request = new CreateCommentRequest();
            request.setPostId(10L);
            request.setContent("Risposta nested!");
            request.setParentId(100L);           // root comment (struttura flat threading)
            request.setReplyToCommentId(101L);   // reply specifica (autore: postAuthor id=1)

            User thirdUser = User.builder().id(3L).username("terzo").build();
            Comment thirdReply = Comment.builder().id(104L).author(thirdUser)
                    .post(post).content("Risposta nested!").parent(rootComment).createdAt(LocalDateTime.now()).build();

            when(postRepository.findById(10L)).thenReturn(Optional.of(post));
            when(userRepository.findById(3L)).thenReturn(Optional.of(thirdUser));
            when(commentRepository.save(any())).thenReturn(thirdReply);
            // Il service chiama findById per parentId=100L prima, poi per replyToCommentId=101L
            when(commentRepository.findById(100L)).thenReturn(Optional.of(rootComment));
            when(commentRepository.findById(101L)).thenReturn(Optional.of(replyComment));
            when(reactionRepository.findByCommentId(anyLong())).thenReturn(Collections.emptyList());

            commentService.create(3L, request);

            // Notifica postAuthor (id=1), autore del replyToCommentId
            verify(notificationService).createNotification(
                    eq(1L), eq(3L), eq(NotificationType.COMMENT), eq(10L), any(), anyString());
        }
    }
}