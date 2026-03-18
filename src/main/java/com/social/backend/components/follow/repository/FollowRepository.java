package com.social.backend.components.follow.repository;

import com.social.backend.components.follow.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    // Verifica se esiste follow
    boolean existsByFollowerIdAndFollowedId(Long followerId, Long followedId);

    // Trova follow specifico
    Optional<Follow> findByFollowerIdAndFollowedId(Long followerId, Long followedId);

    // Trova tutti i follow di un follower
    List<Follow> findByFollowerId(Long followerId);

    // Trova tutti i follower di un utente
    List<Follow> findByFollowedId(Long followedId);

    // Conta followers di un utente
    long countByFollowedId(Long followedId);

    // Conta following di un utente
    long countByFollowerId(Long followerId);

    // QUESTA QUERY SERVE PER IL FEED!
    @Query("SELECT f.followed.id FROM Follow f WHERE f.follower.id = :userId")
    List<Long> findFollowingIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    void deleteByFollowerId(Long followerId);

    @Modifying
    @Transactional
    void deleteByFollowedId(Long followedId);
}