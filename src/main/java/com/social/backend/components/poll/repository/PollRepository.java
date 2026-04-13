package com.social.backend.components.poll.repository;

import com.social.backend.components.poll.entity.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PollRepository extends JpaRepository<Poll, Long> {

    @Query("SELECT p FROM Poll p JOIN FETCH p.options WHERE p.post.id = :postId")
    Optional<Poll> findByPostIdWithOptions(@Param("postId") Long postId);

    boolean existsByPostId(Long postId);
}