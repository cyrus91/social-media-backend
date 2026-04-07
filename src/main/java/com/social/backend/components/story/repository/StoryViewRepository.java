package com.social.backend.components.story.repository;

import com.social.backend.components.story.entity.StoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface StoryViewRepository extends JpaRepository<StoryView, Long> {

    boolean existsByStoryIdAndViewerId(Long storyId, Long viewerId);

    @Query("SELECT sv FROM StoryView sv JOIN FETCH sv.viewer WHERE sv.story.id = :storyId ORDER BY sv.viewedAt DESC")
    List<StoryView> findByStoryId(@Param("storyId") Long storyId);

    // ID delle storie già viste dall'utente
    @Query("SELECT sv.story.id FROM StoryView sv WHERE sv.viewer.id = :viewerId AND sv.story.id IN :storyIds")
    List<Long> findViewedStoryIds(@Param("viewerId") Long viewerId, @Param("storyIds") List<Long> storyIds);

    @Modifying
    @Transactional
    @Query("DELETE FROM StoryView sv WHERE sv.story.id IN (SELECT s.id FROM Story s WHERE s.author.id = :authorId)")
    void deleteByStoryAuthorId(@Param("authorId") Long authorId);

    long countByStoryId(Long storyId);

    @Modifying
    @Transactional
    void deleteByStoryId(Long storyId);

    @Modifying
    @Transactional
    @Query("DELETE FROM StoryView sv WHERE sv.story.id IN (SELECT s.id FROM Story s WHERE s.expiresAt <= :now)")
    int deleteByExpiredStories(@Param("now") java.time.LocalDateTime now);
}