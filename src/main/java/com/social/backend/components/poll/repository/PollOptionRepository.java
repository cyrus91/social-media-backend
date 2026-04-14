package com.social.backend.components.poll.repository;

import com.social.backend.components.poll.entity.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE PollOption o SET o.voteCount = o.voteCount + 1 WHERE o.id = :optionId")
    void incrementVoteCount(@Param("optionId") Long optionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE PollOption o SET o.voteCount = o.voteCount - 1 WHERE o.id = :optionId AND o.voteCount > 0")
    void decrementVoteCount(@Param("optionId") Long optionId);
}