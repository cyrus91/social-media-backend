package com.social.backend.components.poll.service.impl;

import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.poll.dto.CreatePollRequest;
import com.social.backend.components.poll.dto.PollOptionResponse;
import com.social.backend.components.poll.dto.PollResponse;
import com.social.backend.components.poll.entity.Poll;
import com.social.backend.components.poll.entity.PollOption;
import com.social.backend.components.poll.entity.PollVote;
import com.social.backend.components.poll.repository.PollOptionRepository;
import com.social.backend.components.poll.repository.PollRepository;
import com.social.backend.components.poll.repository.PollVoteRepository;
import com.social.backend.components.poll.service.PollService;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PollServiceImpl implements PollService {

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public PollResponse createForPost(Long postId, CreatePollRequest request) {
        var post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato"));

        if (request.getOptions() == null || request.getOptions().size() < 2 || request.getOptions().size() > 4)
            throw new IllegalArgumentException("Il sondaggio deve avere tra 2 e 4 opzioni");

        int hours = request.getDurationHours() > 0 ? request.getDurationHours() : 24;

        Poll poll = Poll.builder()
                .post(post)
                .question(request.getQuestion())
                .expiresAt(LocalDateTime.now().plusHours(hours))
                .build();

        Poll saved = pollRepository.save(poll);

        request.getOptions().forEach(text -> {
            PollOption opt = PollOption.builder()
                    .poll(saved)
                    .text(text.trim())
                    .build();
            pollOptionRepository.save(opt);
        });

        return getByPostId(postId, null);
    }

    @Override
    public PollResponse getByPostId(Long postId, Long currentUserId) {
        Poll poll = pollRepository.findByPostIdWithOptions(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Sondaggio non trovato"));
        return mapToResponse(poll, currentUserId);
    }

    @Override
    @Transactional
    public PollResponse vote(Long pollId, Long optionId, Long userId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new ResourceNotFoundException("Sondaggio non trovato"));

        if (poll.isExpired())
            throw new IllegalStateException("Il sondaggio è scaduto");

        if (pollVoteRepository.existsByPollIdAndUserId(pollId, userId))
            throw new IllegalStateException("Hai già votato in questo sondaggio");

        PollOption option = pollOptionRepository.findById(optionId)
                .orElseThrow(() -> new ResourceNotFoundException("Opzione non trovata"));

        if (!option.getPoll().getId().equals(pollId))
            throw new IllegalArgumentException("Opzione non appartiene a questo sondaggio");

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));

        pollVoteRepository.save(PollVote.builder()
                .poll(poll)
                .option(option)
                .user(user)
                .build());

        pollOptionRepository.incrementVoteCount(optionId);

        // Ricarica con dati aggiornati
        Poll updated = pollRepository.findByPostIdWithOptions(poll.getPost().getId())
                .orElseThrow();
        return mapToResponse(updated, userId);
    }

    private PollResponse mapToResponse(Poll poll, Long currentUserId) {
        long total = poll.getOptions().stream().mapToLong(PollOption::getVoteCount).sum();

        Long votedOptionId = currentUserId != null
                ? pollVoteRepository.findVotedOptionId(poll.getId(), currentUserId).orElse(null)
                : null;

        List<PollOptionResponse> options = poll.getOptions().stream()
                .map(o -> PollOptionResponse.builder()
                        .id(o.getId())
                        .text(o.getText())
                        .voteCount(o.getVoteCount())
                        .percentage(total > 0 ? (o.getVoteCount() * 100.0 / total) : 0)
                        .build())
                .toList();

        return PollResponse.builder()
                .id(poll.getId())
                .question(poll.getQuestion())
                .options(options)
                .totalVotes(total)
                .votedOptionId(votedOptionId)
                .expired(poll.isExpired())
                .expiresAt(poll.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant())
                .build();
    }
}