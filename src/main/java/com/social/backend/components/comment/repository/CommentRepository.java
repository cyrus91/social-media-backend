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

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);

    // Versione non paginata
    // Solo commenti principali (parent == null)
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId AND c.parent IS NULL ORDER BY c.createdAt ASC")
    List<Comment> findByPostId(@Param("postId") Long postId);

    // Risposte a un commento specifico
    @Query("SELECT c FROM Comment c WHERE c.parent.id = :parentId ORDER BY c.createdAt ASC")
    List<Comment> findReplies(@Param("parentId") Long parentId);

    // Versione paginata
    Page<Comment> findByPostId(Long postId, Pageable pageable);

    // conta commenti per post
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