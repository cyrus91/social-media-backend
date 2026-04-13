package com.social.backend.components.poll.repository;

import com.social.backend.components.poll.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    boolean existsByPollIdAndUserId(Long pollId, Long userId);

    @Query("SELECT v.option.id FROM PollVote v WHERE v.poll.id = :pollId AND v.user.id = :userId")
    Optional<Long> findVotedOptionId(@Param("pollId") Long pollId, @Param("userId") Long userId);
}