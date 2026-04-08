package com.social.backend.components.post.repository;

import com.social.backend.components.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

public interface PostRepository extends JpaRepository<Post, Long> {

    // ============================================
    // QUERY OTTIMIZZATE CON FETCH JOIN
    // ============================================

    /**
     * Ottieni tutti i post con JOIN FETCH su author (evita N+1 queries)
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.hidden = false ORDER BY p.createdAt DESC")
    Page<Post> findAllWithAuthor(Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.author.id = :authorId AND p.hidden = false ORDER BY p.createdAt DESC")
    Page<Post> findByAuthorIdWithAuthor(@Param("authorId") Long authorId, Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.author.id IN :followedUserIds AND p.hidden = false ORDER BY p.createdAt DESC")
    Page<Post> findByAuthorIdInWithAuthor(@Param("followedUserIds") List<Long> followedUserIds, Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.author.id NOT IN :excludedAuthorIds AND p.hidden = false ORDER BY p.createdAt DESC")
    Page<Post> findByAuthorIdNotInWithAuthor(@Param("excludedAuthorIds") Set<Long> excludedAuthorIds, Pageable pageable);

    List<Post> findByAuthorId(Long authorId);

    Page<Post> findByAuthorId(Long authorId, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.author.id IN :authorIds AND p.hidden = false ORDER BY p.createdAt DESC")
    Page<Post> findByAuthorIdIn(@Param("authorIds") List<Long> authorIds, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.author.id NOT IN :excludedAuthorIds AND p.hidden = false ORDER BY p.createdAt DESC")
    Page<Post> findByAuthorIdNotIn(@Param("excludedAuthorIds") Set<Long> excludedAuthorIds, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.author.id IN " +
            "(SELECT f.followed.id FROM Follow f WHERE f.follower.id = :userId) " +
            "AND p.hidden = false ORDER BY p.createdAt DESC")
    List<Post> findFeedByUserId(@Param("userId") Long userId);

    @Query("SELECT p FROM Post p WHERE p.author.id IN " +
            "(SELECT f.followed.id FROM Follow f WHERE f.follower.id = :userId) " +
            "AND p.hidden = false ORDER BY p.createdAt DESC")
    Page<Post> findFeedByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.author.id = :authorId AND p.hidden = false")
    Integer countByAuthorId(@Param("authorId") Long authorId);

    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE LOWER(p.content) LIKE LOWER(CONCAT('%#', :tag, '%')) AND p.hidden = false ORDER BY p.createdAt DESC")
    Page<Post> findByHashtag(@Param("tag") String tag, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM Post p WHERE p.author.id = :authorId")
    void deleteByAuthorId(@Param("authorId") Long authorId);

    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :postId")
    void incrementViewCount(@Param("postId") Long postId);

    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.hidden = true WHERE p.id = :postId")
    void hidePost(@Param("postId") Long postId);
}