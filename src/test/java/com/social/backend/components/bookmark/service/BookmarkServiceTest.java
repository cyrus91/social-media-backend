package com.social.backend.components.bookmark.service;

import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.bookmark.entity.Bookmark;
import com.social.backend.components.bookmark.repository.BookmarkRepository;
import com.social.backend.components.bookmark.service.impl.BookmarkServiceImpl;
import com.social.backend.components.comment.repository.CommentRepository;
import com.social.backend.components.like.repository.LikeRepository;
import com.social.backend.components.poll.dto.PollResponse;
import com.social.backend.components.poll.entity.Poll;
import com.social.backend.components.poll.entity.PollOption;
import com.social.backend.components.poll.repository.PollRepository;
import com.social.backend.components.poll.repository.PollVoteRepository;
import com.social.backend.components.post.dto.PostResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookmarkService Unit Tests")
class BookmarkServiceTest {

    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private LikeRepository likeRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private PollRepository pollRepository;
    @Mock private PollVoteRepository pollVoteRepository;

    @InjectMocks
    private BookmarkServiceImpl bookmarkService;

    private User user;
    private Post post;
    private Post postWithPoll;
    private Poll poll;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).username("cirodattilo91").build();
        post = Post.builder().id(10L).author(user).content("Post senza sondaggio")
                .images(new ArrayList<>()).createdAt(LocalDateTime.now()).build();

        PollOption optA = PollOption.builder().id(1L).text("A").voteCount(3L).build();
        PollOption optB = PollOption.builder().id(2L).text("B").voteCount(1L).build();

        poll = Poll.builder().id(5L).question("Test?")
                .options(new ArrayList<>(List.of(optA, optB)))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        postWithPoll = Post.builder().id(11L).author(user).content("Post con sondaggio")
                .images(new ArrayList<>()).createdAt(LocalDateTime.now()).build();
    }

    @Nested
    @DisplayName("toggle")
    class Toggle {

        @Test
        @DisplayName("Dovrebbe aggiungere bookmark se non esistente")
        void shouldAddBookmark() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(postRepository.findById(10L)).thenReturn(Optional.of(post));
            when(bookmarkRepository.existsByUserIdAndPostId(1L, 10L)).thenReturn(false);
            when(bookmarkRepository.save(any())).thenReturn(new Bookmark());

            bookmarkService.toggle(1L, 10L);

            verify(bookmarkRepository).save(any(Bookmark.class));
            verify(bookmarkRepository, never()).deleteByUserIdAndPostId(any(), any());
        }

        @Test
        @DisplayName("Dovrebbe rimuovere bookmark se già esistente")
        void shouldRemoveBookmark() {
            // toggle() controlla existsByUserIdAndPostId prima: se true, elimina direttamente
            // senza chiamare userRepository/postRepository
            when(bookmarkRepository.existsByUserIdAndPostId(1L, 10L)).thenReturn(true);

            bookmarkService.toggle(1L, 10L);

            verify(bookmarkRepository).deleteByUserIdAndPostId(1L, 10L);
            verify(bookmarkRepository, never()).save(any());
        }

        @Test
        @DisplayName("Dovrebbe lanciare eccezione se utente non trovato")
        void shouldThrowIfUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookmarkService.toggle(999L, 10L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getUserBookmarks — con poll")
    class GetUserBookmarksWithPoll {

        @Test
        @DisplayName("Dovrebbe includere il poll nei bookmark del post che lo contiene")
        void shouldIncludePollInBookmarkedPost() {
            Bookmark bookmark = Bookmark.builder().id(1L).user(user).post(postWithPoll).build();

            when(bookmarkRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of(bookmark)));
            when(likeRepository.countByPostId(11L)).thenReturn(0);
            when(commentRepository.countByPostId(11L)).thenReturn(0);
            when(likeRepository.existsByUserIdAndPostId(anyLong(), anyLong())).thenReturn(false);
            when(bookmarkRepository.existsByUserIdAndPostId(anyLong(), anyLong())).thenReturn(true);
            when(pollRepository.findByPostIdWithOptions(11L)).thenReturn(Optional.of(poll));
            when(pollVoteRepository.findVotedOptionId(eq(5L), anyLong())).thenReturn(Optional.empty());

            Page<PostResponse> result = bookmarkService.getUserBookmarks(1L, 1L, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            PostResponse postResponse = result.getContent().get(0);
            assertThat(postResponse.getPoll()).isNotNull();
            assertThat(postResponse.getPoll().getQuestion()).isEqualTo("Test?");
            assertThat(postResponse.getPoll().getTotalVotes()).isEqualTo(4L);
        }

        @Test
        @DisplayName("Dovrebbe restituire poll null per post senza sondaggio")
        void shouldReturnNullPollForPostWithoutPoll() {
            Bookmark bookmark = Bookmark.builder().id(2L).user(user).post(post).build();

            when(bookmarkRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of(bookmark)));
            when(likeRepository.countByPostId(10L)).thenReturn(0);
            when(commentRepository.countByPostId(10L)).thenReturn(0);
            when(likeRepository.existsByUserIdAndPostId(anyLong(), anyLong())).thenReturn(false);
            when(bookmarkRepository.existsByUserIdAndPostId(anyLong(), anyLong())).thenReturn(true);
            when(pollRepository.findByPostIdWithOptions(10L)).thenReturn(Optional.empty());

            Page<PostResponse> result = bookmarkService.getUserBookmarks(1L, 1L, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getPoll()).isNull();
        }

        @Test
        @DisplayName("Poll con eccezione non deve bloccare il caricamento del post")
        void shouldNotFailIfPollThrows() {
            Bookmark bookmark = Bookmark.builder().id(3L).user(user).post(postWithPoll).build();

            when(bookmarkRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of(bookmark)));
            when(likeRepository.countByPostId(11L)).thenReturn(0);
            when(commentRepository.countByPostId(11L)).thenReturn(0);
            when(likeRepository.existsByUserIdAndPostId(anyLong(), anyLong())).thenReturn(false);
            when(bookmarkRepository.existsByUserIdAndPostId(anyLong(), anyLong())).thenReturn(true);
            when(pollRepository.findByPostIdWithOptions(11L)).thenThrow(new RuntimeException("DB error"));

            // Non deve propagare l'eccezione — il try-catch nel mapper la ingloba
            Page<PostResponse> result = bookmarkService.getUserBookmarks(1L, 1L, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getPoll()).isNull();
        }
    }
}