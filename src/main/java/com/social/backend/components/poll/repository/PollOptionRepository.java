package com.social.backend.components.poll.repository;

import com.social.backend.components.poll.entity.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE PollOption o SET o.voteCount = o.voteCount + 1 WHERE o.id = :optionId")
    void incrementVoteCount(@Param("optionId") Long optionId);
}