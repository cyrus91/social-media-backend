package com.social.backend.components.story.repository;

import com.social.backend.components.story.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {

    // Storie attive di un utente (non scadute)
    @Query("SELECT s FROM Story s WHERE s.author.id = :authorId AND s.expiresAt > :now ORDER BY s.createdAt ASC")
    List<Story> findActiveByAuthorId(@Param("authorId") Long authorId, @Param("now") LocalDateTime now);

    // Storie feed: utenti seguiti + proprie storie, non scadute
    @Query("SELECT s FROM Story s WHERE s.author.id IN :authorIds AND s.expiresAt > :now ORDER BY s.createdAt ASC")
    List<Story> findActiveFeedStories(@Param("authorIds") List<Long> authorIds, @Param("now") LocalDateTime now);

    // Conta storie attive per utente
    @Query("SELECT COUNT(s) FROM Story s WHERE s.author.id = :authorId AND s.expiresAt > :now")
    long countActiveByAuthorId(@Param("authorId") Long authorId, @Param("now") LocalDateTime now);

    // Elimina storie scadute (per cleanup schedulato)
    @Modifying
    @Transactional
    @Query("DELETE FROM Story s WHERE s.expiresAt <= :now")
    int deleteExpiredStories(@Param("now") LocalDateTime now);
}