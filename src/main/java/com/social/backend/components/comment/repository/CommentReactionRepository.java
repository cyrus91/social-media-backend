package com.social.backend.components.comment.repository;

import com.social.backend.components.comment.entity.CommentReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    List<CommentReaction> findByCommentId(Long commentId);

    Optional<CommentReaction> findByCommentIdAndUserId(Long commentId, Long userId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM CommentReaction r WHERE r.comment.id = :commentId AND r.user.id = :userId")
    void deleteByCommentIdAndUserId(@Param("commentId") Long commentId, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE CommentReaction r SET r.emoji = :emoji WHERE r.comment.id = :commentId AND r.user.id = :userId")
    void updateEmoji(@Param("commentId") Long commentId, @Param("userId") Long userId, @Param("emoji") String emoji);
}