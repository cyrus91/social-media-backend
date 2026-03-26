package com.social.backend.components.messaging.service.impl;

import com.social.backend.common.exception.ForbiddenException;
import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.messaging.dto.ConversationDTO;
import com.social.backend.components.messaging.dto.MessageDTO;
import com.social.backend.components.messaging.entity.Conversation;
import com.social.backend.components.messaging.entity.Message;
import com.social.backend.components.messaging.entity.MessageReaction;
import com.social.backend.components.messaging.repository.ConversationRepository;
import com.social.backend.components.messaging.repository.MessageReactionRepository;
import com.social.backend.components.messaging.repository.MessageRepository;
import com.social.backend.components.messaging.service.MessagingService;
import com.social.backend.components.storage.service.StorageService;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessagingServiceImpl implements MessagingService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageReactionRepository reactionRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final Set<Long> onlineUsers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public MessagingServiceImpl(ConversationRepository conversationRepository,
                                MessageRepository messageRepository,
                                MessageReactionRepository reactionRepository,
                                UserRepository userRepository,
                                StorageService storageService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.reactionRepository = reactionRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    public void setOnline(Long userId) { onlineUsers.add(userId); }
    public void setOffline(Long userId) { onlineUsers.remove(userId); }
    public boolean isOnline(Long userId) { return onlineUsers.contains(userId); }

    @Override
    @Transactional
    public ConversationDTO getOrCreateConversation(Long userId, Long otherUserId) {
        if (userId.equals(otherUserId)) throw new IllegalArgumentException("Non puoi aprire una chat con te stesso");
        return conversationRepository.findBetweenUsers(userId, otherUserId)
                .map(c -> mapToConversationDTO(c, userId))
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));
                    User other = userRepository.findById(otherUserId)
                            .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));
                    Conversation conv = Conversation.builder().user1(user).user2(other).build();
                    return mapToConversationDTO(conversationRepository.save(conv), userId);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationDTO> getConversations(Long userId) {
        return conversationRepository.findByUserId(userId).stream()
                .map(c -> mapToConversationDTO(c, userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageDTO> getMessages(Long conversationId, Long userId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversazione non trovata"));
        if (!conv.getUser1().getId().equals(userId) && !conv.getUser2().getId().equals(userId))
            throw new ForbiddenException("Non puoi leggere questa conversazione");
        return messageRepository.findAllByConversationId(conversationId)
                .stream().map(m -> mapToMessageDTO(m, userId)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MessageDTO sendMessage(Long conversationId, Long senderId, String content, Long replyToId) {
        Conversation conv = getConversationAndValidate(conversationId, senderId);
        User sender = getUser(senderId);
        Message.MessageBuilder builder = Message.builder()
                .conversation(conv).sender(sender).content(content).isRead(false);
        if (replyToId != null) {
            messageRepository.findById(replyToId).ifPresent(original -> {
                builder.replyToId(original.getId());
                builder.replyToContent(original.isDeletedForAll() ? "Messaggio eliminato" : original.getContent());
                builder.replyToSenderUsername(original.getSender().getUsername());
            });
        }
        // Eredita expiresAt dalla conversazione se impostato
        builder.expiresAt(conv.getDisappearingHours() != null
                ? LocalDateTime.now().plusHours(conv.getDisappearingHours()) : null);
        Message saved = messageRepository.save(builder.build());
        conversationRepository.touchUpdatedAt(conv.getId());
        return mapToMessageDTO(saved, senderId);
    }

    @Override
    @Transactional
    public MessageDTO sendMessageWithImage(Long conversationId, Long senderId, String content, MultipartFile image, Long replyToId) {
        Conversation conv = getConversationAndValidate(conversationId, senderId);
        User sender = getUser(senderId);
        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            String publicId = storageService.store(image, "messages/" + conversationId);
            imageUrl = storageService.getFileUrl(publicId, "messages/" + conversationId);
        }
        Message.MessageBuilder builder = Message.builder()
                .conversation(conv).sender(sender).content(content).imageUrl(imageUrl).isRead(false);
        if (replyToId != null) {
            messageRepository.findById(replyToId).ifPresent(original -> {
                builder.replyToId(original.getId());
                builder.replyToContent(original.isDeletedForAll() ? "Messaggio eliminato" : original.getContent());
                builder.replyToSenderUsername(original.getSender().getUsername());
            });
        }
        builder.expiresAt(conv.getDisappearingHours() != null
                ? LocalDateTime.now().plusHours(conv.getDisappearingHours()) : null);
        Message saved = messageRepository.save(builder.build());
        conversationRepository.touchUpdatedAt(conv.getId());
        return mapToMessageDTO(saved, senderId);
    }

    @Override
    @Transactional
    public MessageDTO sendVoiceMessage(Long conversationId, Long senderId, MultipartFile audio) {
        Conversation conv = getConversationAndValidate(conversationId, senderId);
        User sender = getUser(senderId);
        String audioUrl = null;
        if (audio != null && !audio.isEmpty()) {
            String publicId = storageService.storeRaw(audio, "voice/" + conversationId);
            audioUrl = storageService.getFileUrl(publicId, "voice/" + conversationId);
        }
        Message saved = messageRepository.save(Message.builder()
                .conversation(conv).sender(sender).audioUrl(audioUrl).isRead(false)
                .expiresAt(conv.getDisappearingHours() != null
                        ? LocalDateTime.now().plusHours(conv.getDisappearingHours()) : null)
                .build());
        conversationRepository.touchUpdatedAt(conv.getId());
        return mapToMessageDTO(saved, senderId);
    }

    @Override
    @Transactional
    public void markAsRead(Long conversationId, Long userId) {
        messageRepository.markAllAsRead(conversationId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return messageRepository.countAllUnreadForUser(userId);
    }

    @Override
    @Transactional
    public void deleteMessageForAll(Long messageId, Long requesterId) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Messaggio non trovato"));
        if (!msg.getSender().getId().equals(requesterId))
            throw new ForbiddenException("Non puoi eliminare questo messaggio");
        msg.setDeletedForAll(true);
        msg.setContent(null);
        msg.setImageUrl(null);
        msg.setAudioUrl(null);
        messageRepository.save(msg);
    }

    @Override
    @Transactional
    public MessageDTO toggleReaction(Long messageId, Long userId, String emoji) {
        messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Messaggio non trovato"));
        User user = getUser(userId);

        Optional<MessageReaction> existing = reactionRepository.findByMessageIdAndUserId(messageId, userId);
        if (existing.isPresent()) {
            if (existing.get().getEmoji().equals(emoji)) {
                // Stessa emoji → rimuovi
                reactionRepository.deleteByMessageIdAndUserId(messageId, userId);
            } else {
                // Emoji diversa → aggiorna direttamente con query UPDATE (evita cache Hibernate)
                reactionRepository.updateEmoji(messageId, userId, emoji);
            }
        } else {
            // Nuova reazione — recupera messaggio fresh dal DB
            Message freshMsg = messageRepository.findById(messageId).get();
            reactionRepository.save(MessageReaction.builder()
                    .message(freshMsg).user(user).emoji(emoji).build());
        }

        // flush + ricarica FRESH dal DB per avere la lista reactions aggiornata
        reactionRepository.flush();
        Message updated = messageRepository.findByIdWithReactions(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Messaggio non trovato"));
        return mapToMessageDTO(updated, userId);
    }

    @Override
    @Transactional
    public void setDisappearingMessages(Long conversationId, Long userId, Integer hoursToExpire) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversazione non trovata"));
        if (!conv.getUser1().getId().equals(userId) && !conv.getUser2().getId().equals(userId))
            throw new ForbiddenException("Non autorizzato");
        conv.setDisappearingHours(hoursToExpire);
        conversationRepository.save(conv);
    }

    // ============================================
    // HELPERS
    // ============================================

    private Conversation getConversationAndValidate(Long conversationId, Long userId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversazione non trovata"));
        if (!conv.getUser1().getId().equals(userId) && !conv.getUser2().getId().equals(userId))
            throw new ForbiddenException("Non puoi inviare messaggi in questa conversazione");
        return conv;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));
    }

    private ConversationDTO mapToConversationDTO(Conversation conv, Long currentUserId) {
        User other = conv.getUser1().getId().equals(currentUserId) ? conv.getUser2() : conv.getUser1();
        String lastMsg = null;
        LocalDateTime lastMsgAt = conv.getUpdatedAt();
        var lastMessage = messageRepository.findLastMessage(conv.getId());
        if (lastMessage.isPresent()) {
            Message last = lastMessage.get();
            if (last.isDeletedForAll()) lastMsg = "Messaggio eliminato";
            else if (last.getAudioUrl() != null) lastMsg = "🎤 Vocale";
            else if (last.getImageUrl() != null) lastMsg = "📷 Foto";
            else lastMsg = last.getContent();
            lastMsgAt = last.getCreatedAt();
        }
        long unread = messageRepository.countUnread(conv.getId(), currentUserId);
        return ConversationDTO.builder()
                .id(conv.getId())
                .otherUserId(other.getId())
                .otherUsername(other.getUsername())
                .otherAvatarUrl(other.getAvatarUrl())
                .otherOnline(isOnline(other.getId()))
                .lastMessage(lastMsg)
                .lastMessageAt(lastMsgAt)
                .unreadCount(unread)
                .disappearingHours(conv.getDisappearingHours())
                .build();
    }

    private MessageDTO mapToMessageDTO(Message msg, Long currentUserId) {
        // Raggruppa reazioni: emoji → count
        Map<String, Long> reactions = msg.getReactions().stream()
                .collect(Collectors.groupingBy(MessageReaction::getEmoji, Collectors.counting()));
        String myReaction = msg.getReactions().stream()
                .filter(r -> r.getUser().getId().equals(currentUserId))
                .map(MessageReaction::getEmoji)
                .findFirst().orElse(null);

        return MessageDTO.builder()
                .id(msg.getId())
                .conversationId(msg.getConversation().getId())
                .senderId(msg.getSender().getId())
                .senderUsername(msg.getSender().getUsername())
                .senderAvatarUrl(msg.getSender().getAvatarUrl())
                .content(msg.isDeletedForAll() ? null : msg.getContent())
                .imageUrl(msg.isDeletedForAll() ? null : msg.getImageUrl())
                .audioUrl(msg.isDeletedForAll() ? null : msg.getAudioUrl())
                .replyToId(msg.getReplyToId())
                .replyToContent(msg.getReplyToContent())
                .replyToSenderUsername(msg.getReplyToSenderUsername())
                .isRead(msg.isRead())
                .deletedForAll(msg.isDeletedForAll())
                .expiresAt(msg.getExpiresAt())
                .createdAt(msg.getCreatedAt())
                .reactions(reactions)
                .myReaction(myReaction)
                .build();
    }
}