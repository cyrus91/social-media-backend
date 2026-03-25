package com.social.backend.components.messaging.controller;

import com.social.backend.components.messaging.dto.ConversationDTO;
import com.social.backend.components.messaging.dto.MessageDTO;
import com.social.backend.components.messaging.repository.ConversationRepository;
import com.social.backend.components.messaging.service.MessagingService;
import com.social.backend.components.user.entity.User;
import com.social.backend.config.RedisPubSubService;
import com.social.backend.security.UserDetailsImpl;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessagingController {

    private final MessagingService messagingService;
    private final RedisPubSubService redisPubSubService;
    private final ConversationRepository conversationRepository;

    public MessagingController(MessagingService messagingService,
                               RedisPubSubService redisPubSubService,
                               ConversationRepository conversationRepository) {
        this.messagingService = messagingService;
        this.redisPubSubService = redisPubSubService;
        this.conversationRepository = conversationRepository;
    }

    @GetMapping("/conversations")
    public List<ConversationDTO> getConversations(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return messagingService.getConversations(userDetails.getUser().getId());
    }

    @PostMapping("/conversations/{otherUserId}")
    public ConversationDTO getOrCreateConversation(
            @PathVariable Long otherUserId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return messagingService.getOrCreateConversation(
                userDetails.getUser().getId(), otherUserId);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public Page<MessageDTO> getMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long userId = userDetails.getUser().getId();
        Page<MessageDTO> messages = messagingService.getMessages(conversationId, userId, page, size);
        markReadAndNotify(conversationId, userId);
        return messages;
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDTO sendMessage(
            @PathVariable Long conversationId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        MessageDTO msg = messagingService.sendMessage(
                conversationId, userDetails.getUser().getId(), body.get("content"));
        publishToOther(conversationId, userDetails.getUser(), msg);
        return msg;
    }

    @PostMapping(value = "/conversations/{conversationId}/messages/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDTO sendMessageWithImage(
            @PathVariable Long conversationId,
            @RequestParam(required = false) String content,
            @RequestParam("image") MultipartFile image,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        MessageDTO msg = messagingService.sendMessageWithImage(
                conversationId, userDetails.getUser().getId(), content, image);
        publishToOther(conversationId, userDetails.getUser(), msg);
        return msg;
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return Map.of("count", messagingService.countUnread(userDetails.getUser().getId()));
    }

    @PutMapping("/conversations/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        markReadAndNotify(conversationId, userDetails.getUser().getId());
    }

    // ============================================
    // HELPERS
    // ============================================

    /**
     * Pubblica il messaggio al destinatario via Redis.
     * Usa ConversationRepository direttamente — query semplice, nessun rischio di eccezioni.
     */
    private void publishToOther(Long conversationId, User sender, MessageDTO msg) {
        try {
            conversationRepository.findById(conversationId).ifPresent(conv -> {
                Long otherUserId = conv.getUser1().getId().equals(sender.getId())
                        ? conv.getUser2().getId()
                        : conv.getUser1().getId();
                redisPubSubService.publish("/queue/messages/" + otherUserId, msg);
            });
        } catch (Exception e) {
            // Non far fallire il send se il publish fallisce
            System.err.println("⚠️ Errore publish Redis: " + e.getMessage());
        }
    }

    private void markReadAndNotify(Long conversationId, Long userId) {
        messagingService.markAsRead(conversationId, userId);
        try {
            conversationRepository.findById(conversationId).ifPresent(conv -> {
                Long otherUserId = conv.getUser1().getId().equals(userId)
                        ? conv.getUser2().getId()
                        : conv.getUser1().getId();
                redisPubSubService.publish(
                        "/queue/read-receipt/" + otherUserId,
                        Map.of("conversationId", conversationId, "readBy", userId)
                );
            });
        } catch (Exception e) {
            System.err.println("⚠️ Errore publish read receipt: " + e.getMessage());
        }
    }


}
