package com.social.backend.components.messaging.service.impl;

import com.social.backend.common.exception.ForbiddenException;
import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.messaging.dto.ConversationDTO;
import com.social.backend.components.messaging.dto.MessageDTO;
import com.social.backend.components.messaging.entity.Conversation;
import com.social.backend.components.messaging.entity.Message;
import com.social.backend.components.messaging.repository.ConversationRepository;
import com.social.backend.components.messaging.repository.MessageRepository;
import com.social.backend.components.messaging.service.MessagingService;
import com.social.backend.components.storage.service.StorageService;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MessagingServiceImpl implements MessagingService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    // Tiene traccia degli utenti online (userId)
    private final Set<Long> onlineUsers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public MessagingServiceImpl(ConversationRepository conversationRepository,
                                MessageRepository messageRepository,
                                UserRepository userRepository,
                                StorageService storageService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
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
                    Conversation conv = Conversation.builder()
                            .user1(user).user2(other).build();
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
    public Page<MessageDTO> getMessages(Long conversationId, Long userId, int page, int size) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversazione non trovata"));
        if (!conv.getUser1().getId().equals(userId) && !conv.getUser2().getId().equals(userId)) {
            throw new ForbiddenException("Non puoi leggere questa conversazione");
        }
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(
                conversationId, PageRequest.of(page, size)).map(this::mapToMessageDTO);
    }

    @Override
    @Transactional
    public MessageDTO sendMessage(Long conversationId, Long senderId, String content) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversazione non trovata"));
        if (!conv.getUser1().getId().equals(senderId) && !conv.getUser2().getId().equals(senderId)) {
            throw new ForbiddenException("Non puoi inviare messaggi in questa conversazione");
        }
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));
        Message msg = Message.builder()
                .conversation(conv).sender(sender).content(content).isRead(false).build();
        Message saved = messageRepository.save(msg);
        // Aggiorna solo updatedAt — NON fare save(conv) con orphanRemoval!
        conversationRepository.touchUpdatedAt(conv.getId());
        return mapToMessageDTO(saved);
    }

    @Override
    @Transactional
    public MessageDTO sendMessageWithImage(Long conversationId, Long senderId, String content, MultipartFile image) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversazione non trovata"));
        if (!conv.getUser1().getId().equals(senderId) && !conv.getUser2().getId().equals(senderId)) {
            throw new ForbiddenException("Non puoi inviare messaggi in questa conversazione");
        }
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));

        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            String publicId = storageService.store(image, "messages/" + conversationId);
            imageUrl = storageService.getFileUrl(publicId, "messages/" + conversationId);
        }

        Message msg = Message.builder()
                .conversation(conv).sender(sender)
                .content(content).imageUrl(imageUrl).isRead(false).build();
        messageRepository.save(msg);
        conversationRepository.touchUpdatedAt(conv.getId());
        return mapToMessageDTO(msg);
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

    private ConversationDTO mapToConversationDTO(Conversation conv, Long currentUserId) {
        User other = conv.getUser1().getId().equals(currentUserId) ? conv.getUser2() : conv.getUser1();

        // Ultimo messaggio
        var lastPage = messageRepository.findLastMessage(conv.getId(), PageRequest.of(0, 1));
        String lastMsg = null;
        java.time.LocalDateTime lastMsgAt = conv.getUpdatedAt();
        if (!lastPage.isEmpty()) {
            Message last = lastPage.getContent().get(0);
            lastMsg = last.getImageUrl() != null ? "📷 Foto" : last.getContent();
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
                .build();
    }

    private MessageDTO mapToMessageDTO(Message msg) {
        return MessageDTO.builder()
                .id(msg.getId())
                .conversationId(msg.getConversation().getId())
                .senderId(msg.getSender().getId())
                .senderUsername(msg.getSender().getUsername())
                .senderAvatarUrl(msg.getSender().getAvatarUrl())
                .content(msg.getContent())
                .imageUrl(msg.getImageUrl())
                .isRead(msg.getIsRead())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}