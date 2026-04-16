package com.social.backend.components.poll.service;

import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.poll.dto.CreatePollRequest;
import com.social.backend.components.poll.dto.PollResponse;
import com.social.backend.components.poll.entity.Poll;
import com.social.backend.components.poll.entity.PollOption;
import com.social.backend.components.poll.entity.PollVote;
import com.social.backend.components.poll.repository.PollOptionRepository;
import com.social.backend.components.poll.repository.PollRepository;
import com.social.backend.components.poll.repository.PollVoteRepository;
import com.social.backend.components.poll.service.impl.PollServiceImpl;
import com.social.backend.components.post.entity.Post;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
@DisplayName("PollService Unit Tests")
class PollServiceTest {

    @Mock private PollRepository pollRepository;
    @Mock private PollOptionRepository pollOptionRepository;
    @Mock private PollVoteRepository pollVoteRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private PollServiceImpl pollService;

    private User author;
    private Post post;
    private Poll poll;
    private PollOption option1;
    private PollOption option2;

    @BeforeEach
    void setUp() {
        author = User.builder().id(1L).username("cirodattilo91").build();
        post = Post.builder().id(10L).author(author).content("Test post").build();

        option1 = PollOption.builder().id(1L).text("Opzione A").voteCount(0L).build();
        option2 = PollOption.builder().id(2L).text("Opzione B").voteCount(0L).build();

        poll = Poll.builder()
                .id(5L)
                .question("Domanda di prova?")
                .options(new ArrayList<>(List.of(option1, option2)))
                .post(post)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        option1.setPoll(poll);
        option2.setPoll(poll);

        // @PersistenceContext non viene iniettato da @InjectMocks — inject manuale
        ReflectionTestUtils.setField(pollService, "entityManager", entityManager);
    }

    // ─────────────────────────────────────────────────────────────
    // createForPost
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("createForPost")
    class CreateForPost {

        @Test
        @DisplayName("Dovrebbe creare un sondaggio con opzioni valide")
        void shouldCreatePollWithValidOptions() {
            CreatePollRequest request = new CreatePollRequest();
            request.setQuestion("Domanda?");
            request.setOptions(List.of("Sì", "No"));
            request.setDurationHours(24);

            when(postRepository.findById(10L)).thenReturn(Optional.of(post));
            when(pollRepository.save(any())).thenReturn(poll);
            when(pollRepository.findByPostIdWithOptions(10L)).thenReturn(Optional.of(poll));

            PollResponse result = pollService.createForPost(10L, request);

            assertThat(result).isNotNull();
            assertThat(result.getQuestion()).isEqualTo("Domanda di prova?");
            verify(pollRepository).save(any(Poll.class));
            verify(pollOptionRepository, times(2)).save(any(PollOption.class));
        }

        @Test
        @DisplayName("Dovrebbe lanciare eccezione se il post non esiste")
        void shouldThrowIfPostNotFound() {
            CreatePollRequest request = new CreatePollRequest();
            request.setQuestion("Domanda?");
            request.setOptions(List.of("A", "B"));
            request.setDurationHours(24);

            when(postRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pollService.createForPost(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // vote
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("vote")
    class Vote {

        @Test
        @DisplayName("Dovrebbe registrare il voto e aggiornare il contatore")
        void shouldRegisterVoteAndUpdateCount() {
            User voter = User.builder().id(2L).username("voter").build();

            when(pollRepository.findById(5L)).thenReturn(Optional.of(poll));
            when(pollOptionRepository.findById(1L)).thenReturn(Optional.of(option1));
            when(pollVoteRepository.findVotedOptionId(5L, 2L)).thenReturn(Optional.empty());
            when(userRepository.findById(2L)).thenReturn(Optional.of(voter));
            when(pollVoteRepository.save(any())).thenReturn(new PollVote());
            when(pollRepository.findByPostIdWithOptions(10L)).thenReturn(Optional.of(poll));

            PollResponse result = pollService.vote(5L, 1L, 2L);

            assertThat(result).isNotNull();
            verify(pollOptionRepository).incrementVoteCount(1L);
            verify(pollVoteRepository).save(any(PollVote.class));
            verify(entityManager).flush();
            verify(entityManager).clear();
        }

        @Test
        @DisplayName("Dovrebbe cambiare voto: decrement vecchio, increment nuovo")
        void shouldChangeVoteCorrectly() {
            User voter = User.builder().id(2L).username("voter").build();
            option1.setVoteCount(1L); // aveva già votato option1

            when(pollRepository.findById(5L)).thenReturn(Optional.of(poll));
            when(pollOptionRepository.findById(2L)).thenReturn(Optional.of(option2));
            // Già votato option1 precedentemente
            when(pollVoteRepository.findVotedOptionId(5L, 2L)).thenReturn(Optional.of(1L));
            when(userRepository.findById(2L)).thenReturn(Optional.of(voter));
            when(pollVoteRepository.save(any())).thenReturn(new PollVote());
            when(pollRepository.findByPostIdWithOptions(10L)).thenReturn(Optional.of(poll));

            pollService.vote(5L, 2L, 2L);

            // Deve decrementare option1 e incrementare option2
            verify(pollVoteRepository).deleteByPollIdAndUserId(5L, 2L);
            verify(pollOptionRepository).decrementVoteCount(1L);
            verify(pollOptionRepository).incrementVoteCount(2L);
        }

        @Test
        @DisplayName("Dovrebbe lanciare eccezione se il sondaggio è scaduto")
        void shouldThrowIfPollExpired() {
            poll.setExpiresAt(LocalDateTime.now().minusHours(1)); // scaduto

            when(pollRepository.findById(5L)).thenReturn(Optional.of(poll));

            assertThatThrownBy(() -> pollService.vote(5L, 1L, 2L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("scaduto");
        }

        @Test
        @DisplayName("Dovrebbe lanciare eccezione se l'opzione non appartiene al sondaggio")
        void shouldThrowIfOptionNotBelongToPoll() {
            Poll otherPoll = Poll.builder().id(99L).build();
            PollOption foreignOption = PollOption.builder().id(99L).poll(otherPoll).text("Altra").build();

            when(pollRepository.findById(5L)).thenReturn(Optional.of(poll));
            when(pollOptionRepository.findById(99L)).thenReturn(Optional.of(foreignOption));

            assertThatThrownBy(() -> pollService.vote(5L, 99L, 2L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // updatePoll
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("updatePoll")
    class UpdatePoll {

        @Test
        @DisplayName("Dovrebbe aggiornare domanda e opzioni se nessuno ha votato")
        void shouldUpdatePollWhenNoVotes() {
            when(pollRepository.findById(5L)).thenReturn(Optional.of(poll));
            when(pollRepository.findById(5L)).thenReturn(Optional.of(poll)); // dopo clear
            when(pollRepository.save(any())).thenReturn(poll);
            when(pollRepository.findByPostIdWithOptions(10L)).thenReturn(Optional.of(poll));

            PollResponse result = pollService.updatePoll(5L, 1L, "Nuova domanda?", List.of("X", "Y", "Z"));

            assertThat(result).isNotNull();
            verify(pollRepository).save(any(Poll.class));
            verify(entityManager, atLeastOnce()).flush();
            verify(entityManager, atLeastOnce()).clear();
        }

        @Test
        @DisplayName("Dovrebbe lanciare eccezione se ci sono già voti")
        void shouldThrowIfPollHasVotes() {
            option1.setVoteCount(3L); // qualcuno ha votato

            when(pollRepository.findById(5L)).thenReturn(Optional.of(poll));

            assertThatThrownBy(() -> pollService.updatePoll(5L, 1L, "Nuova?", List.of("A", "B")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Dovrebbe lanciare ForbiddenException se non sei l'autore")
        void shouldThrowIfNotAuthor() {
            when(pollRepository.findById(5L)).thenReturn(Optional.of(poll));

            assertThatThrownBy(() -> pollService.updatePoll(5L, 999L, "Hack?", List.of("A", "B")))
                    .isInstanceOf(com.social.backend.common.exception.ForbiddenException.class);
        }
    }
}