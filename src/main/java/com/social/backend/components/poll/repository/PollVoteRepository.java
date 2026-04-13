package com.social.backend.components.poll.repository;

import com.social.backend.components.poll.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    boolean existsByPollIdAndUserId(Long pollId, Long userId);

    @Query("SELECT v.option.id FROM PollVote v WHERE v.poll.id = :pollId AND v.user.id = :userId")
    Optional<Long> findVotedOptionId(@Param("pollId") Long pollId, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM PollVote v WHERE v.poll.id = :pollId AND v.user.id = :userId")
    void deleteByPollIdAndUserId(@Param("pollId") Long pollId, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM PollVote v WHERE v.poll.id = :pollId")
    void deleteByPollId(@Param("pollId") Long pollId);
}