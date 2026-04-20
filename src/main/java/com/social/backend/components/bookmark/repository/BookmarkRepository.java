package com.social.backend.components.bookmark.repository;

import com.social.backend.components.bookmark.entity.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    void deleteByUserIdAndPostId(Long userId, Long postId);

    @Query("SELECT b FROM Bookmark b JOIN FETCH b.post JOIN FETCH b.post.author WHERE b.user.id = :userId ORDER BY b.createdAt DESC")
    Page<Bookmark> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);

    void deleteByPostId(Long postId);

    long countByPostId(Long postId);
}