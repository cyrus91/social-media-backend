package com.social.backend.components.like.repository;

import com.social.backend.components.like.entity.Like;
import com.social.backend.components.like.entity.LikeId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface LikeRepository extends JpaRepository<Like, LikeId> {  // <-- Usa LikeId come chiave

    List<Like> findByPostId(Long postId);

    int countByPostId(Long postId);

    // verifica se esiste like per utente e post
    boolean existsByUserIdAndPostId(Long userId, Long postId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Like l WHERE l.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Like l WHERE l.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    Page<Like> findByPostId(Long postId, Pageable pageable);

}