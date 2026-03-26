package com.social.backend.components.messaging.service;

import com.social.backend.components.messaging.dto.ConversationDTO;
import com.social.backend.components.messaging.dto.MessageDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MessagingService {

    ConversationDTO getOrCreateConversation(Long userId, Long otherUserId);

    List<ConversationDTO> getConversations(Long userId);

    List<MessageDTO> getMessages(Long conversationId, Long userId);

    MessageDTO sendMessage(Long conversationId, Long senderId, String content, Long replyToId);

    MessageDTO sendMessageWithImage(Long conversationId, Long senderId, String content, MultipartFile image, Long replyToId);

    MessageDTO sendVoiceMessage(Long conversationId, Long senderId, MultipartFile audio);

    void markAsRead(Long conversationId, Long userId);

    long countUnread(Long userId);

    // Delete
    void deleteMessageForAll(Long messageId, Long requesterId);

    // Reactions
    MessageDTO toggleReaction(Long messageId, Long userId, String emoji);

    // Disappearing messages
    void setDisappearingMessages(Long conversationId, Long userId, Integer hoursToExpire);
}