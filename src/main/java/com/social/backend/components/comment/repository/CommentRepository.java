package com.social.backend.components.comment.repository;

import com.social.backend.components.comment.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Solo commenti principali (parent IS NULL) — lista
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId AND c.parent IS NULL ORDER BY c.createdAt ASC")
    List<Comment> findByPostId(@Param("postId") Long postId);

    // Solo commenti principali (parent IS NULL) — paginata
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId AND c.parent IS NULL ORDER BY c.createdAt ASC")
    Page<Comment> findByPostId(@Param("postId") Long postId, Pageable pageable);

    // Risposte a un commento specifico
    @Query("SELECT c FROM Comment c WHERE c.parent.id = :parentId ORDER BY c.createdAt ASC")
    List<Comment> findReplies(@Param("parentId") Long parentId);

    // postId diretto senza lazy loading — per le notifiche in toggleReaction
    @Query("SELECT c.post.id FROM Comment c WHERE c.id = :id")
    Optional<Long> findPostIdById(@Param("id") Long id);

    // Commento con post e author già fetchati — evita LazyInitializationException
    @Query("SELECT c FROM Comment c JOIN FETCH c.post JOIN FETCH c.author WHERE c.id = :id")
    Optional<Comment> findByIdWithPost(@Param("id") Long id);

    int countByPostId(Long postId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Comment c WHERE c.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Comment c WHERE c.author.id = :authorId")
    void deleteByAuthorId(@Param("authorId") Long authorId);
}