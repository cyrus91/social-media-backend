package com.social.backend.components.messaging.repository;

import com.social.backend.components.messaging.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    Optional<MessageReaction> findByMessageIdAndUserId(Long messageId, Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM MessageReaction r WHERE r.message.id = :messageId AND r.user.id = :userId")
    void deleteByMessageIdAndUserId(@Param("messageId") Long messageId, @Param("userId") Long userId);
}