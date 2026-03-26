package com.social.backend.components.messaging.controller;

import com.social.backend.components.messaging.dto.ConversationDTO;
import com.social.backend.components.messaging.dto.MessageDTO;
import com.social.backend.components.messaging.repository.ConversationRepository;
import com.social.backend.components.messaging.repository.MessageRepository;
import com.social.backend.components.messaging.service.MessagingService;
import com.social.backend.components.user.entity.User;
import com.social.backend.config.RedisPubSubService;
import com.social.backend.security.UserDetailsImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
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
    private final MessageRepository messageRepository;

    public MessagingController(MessagingService messagingService,
                               RedisPubSubService redisPubSubService,
                               ConversationRepository conversationRepository,
                               MessageRepository messageRepository) {
        this.messagingService = messagingService;
        this.redisPubSubService = redisPubSubService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @GetMapping("/conversations")
    public List<ConversationDTO> getConversations(@AuthenticationPrincipal UserDetailsImpl u) {
        return messagingService.getConversations(u.getUser().getId());
    }

    @PostMapping("/conversations/{otherUserId}")
    public ConversationDTO getOrCreateConversation(@PathVariable Long otherUserId,
                                                   @AuthenticationPrincipal UserDetailsImpl u) {
        return messagingService.getOrCreateConversation(u.getUser().getId(), otherUserId);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<MessageDTO> getMessages(@PathVariable Long conversationId,
                                        @AuthenticationPrincipal UserDetailsImpl u) {
        Long userId = u.getUser().getId();
        List<MessageDTO> messages = messagingService.getMessages(conversationId, userId);
        markReadAndNotify(conversationId, userId);
        return messages;
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDTO sendMessage(@PathVariable Long conversationId,
                                  @RequestBody Map<String, Object> body,
                                  @AuthenticationPrincipal UserDetailsImpl u) {
        String content = (String) body.get("content");
        Long replyToId = body.get("replyToId") != null
                ? Long.valueOf(body.get("replyToId").toString()) : null;
        MessageDTO msg = messagingService.sendMessage(conversationId, u.getUser().getId(), content, replyToId);
        publishToOther(conversationId, u.getUser(), msg);
        return msg;
    }

    @PostMapping(value = "/conversations/{conversationId}/messages/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDTO sendMessageWithImage(@PathVariable Long conversationId,
                                           @RequestParam(required = false) String content,
                                           @RequestParam("image") MultipartFile image,
                                           @RequestParam(required = false) Long replyToId,
                                           @AuthenticationPrincipal UserDetailsImpl u) {
        MessageDTO msg = messagingService.sendMessageWithImage(
                conversationId, u.getUser().getId(), content, image, replyToId);
        publishToOther(conversationId, u.getUser(), msg);
        return msg;
    }

    @PostMapping(value = "/conversations/{conversationId}/messages/voice",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDTO sendVoiceMessage(@PathVariable Long conversationId,
                                       @RequestParam("audio") MultipartFile audio,
                                       @AuthenticationPrincipal UserDetailsImpl u) {
        MessageDTO msg = messagingService.sendVoiceMessage(conversationId, u.getUser().getId(), audio);
        publishToOther(conversationId, u.getUser(), msg);
        return msg;
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(@AuthenticationPrincipal UserDetailsImpl u) {
        return Map.of("count", messagingService.countUnread(u.getUser().getId()));
    }

    @PutMapping("/conversations/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable Long conversationId,
                           @AuthenticationPrincipal UserDetailsImpl u) {
        markReadAndNotify(conversationId, u.getUser().getId());
    }

    @DeleteMapping("/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable Long messageId,
                              @AuthenticationPrincipal UserDetailsImpl u) {
        // Recupera conversationId prima di cancellare
        messageRepository.findById(messageId).ifPresent(msg -> {
            Long conversationId = msg.getConversation().getId();
            messagingService.deleteMessageForAll(messageId, u.getUser().getId());
            // Notifica l'altro utente
            conversationRepository.findById(conversationId).ifPresent(conv -> {
                Long otherUserId = conv.getUser1().getId().equals(u.getUser().getId())
                        ? conv.getUser2().getId() : conv.getUser1().getId();
                redisPubSubService.publish("/queue/message-deleted/" + otherUserId,
                        Map.of("messageId", messageId, "conversationId", conversationId));
            });
        });
    }

    @PostMapping("/messages/{messageId}/reactions")
    public MessageDTO toggleReaction(@PathVariable Long messageId,
                                     @RequestBody Map<String, String> body,
                                     @AuthenticationPrincipal UserDetailsImpl u) {
        MessageDTO updated = messagingService.toggleReaction(messageId, u.getUser().getId(), body.get("emoji"));
        // Notifica SOLO l'altro utente — chi ha fatto la reazione aggiorna già lo state locale
        conversationRepository.findById(updated.getConversationId()).ifPresent(conv -> {
            Long otherUserId = conv.getUser1().getId().equals(u.getUser().getId())
                    ? conv.getUser2().getId() : conv.getUser1().getId();
            redisPubSubService.publish("/queue/reaction/" + otherUserId, updated);
        });
        return updated;
    }

    @PutMapping("/conversations/{conversationId}/disappearing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setDisappearing(@PathVariable Long conversationId,
                                @RequestBody Map<String, Object> body,
                                @AuthenticationPrincipal UserDetailsImpl u) {
        Integer hours = body.get("hours") != null
                ? Integer.valueOf(body.get("hours").toString()) : null;
        messagingService.setDisappearingMessages(conversationId, u.getUser().getId(), hours);
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            Long otherUserId = conv.getUser1().getId().equals(u.getUser().getId())
                    ? conv.getUser2().getId() : conv.getUser1().getId();
            redisPubSubService.publish("/queue/disappearing/" + otherUserId,
                    Map.of("conversationId", conversationId, "hours", hours != null ? hours : 0));
        });
    }

    // TYPING — via STOMP @MessageMapping (prefix /app/typing)
    @MessageMapping("/typing")
    public void handleTyping(@Payload Map<String, Object> payload,
                             org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        // Con @MessageMapping il Principal viene da headerAccessor, non da @AuthenticationPrincipal
        java.security.Principal principal = headerAccessor.getUser();
        if (principal == null) return;

        String username = null;
        if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth) {
            Object p = auth.getPrincipal();
            if (p instanceof com.social.backend.security.UserDetailsImpl ud) {
                username = ud.getUsername();
            }
        }
        if (username == null) username = principal.getName();

        final String finalUsername = username;
        Long conversationId = Long.valueOf(payload.get("conversationId").toString());
        boolean isTyping = Boolean.parseBoolean(payload.get("isTyping").toString());

        conversationRepository.findById(conversationId).ifPresent(conv -> {
            // Trova l'utente dal repository tramite username
            Long senderId = null;
            if (conv.getUser1().getUsername().equals(finalUsername)) senderId = conv.getUser1().getId();
            else if (conv.getUser2().getUsername().equals(finalUsername)) senderId = conv.getUser2().getId();
            if (senderId == null) return;

            Long otherUserId = conv.getUser1().getId().equals(senderId)
                    ? conv.getUser2().getId() : conv.getUser1().getId();
            redisPubSubService.publish("/queue/typing/" + otherUserId,
                    Map.of("conversationId", conversationId,
                            "userId", senderId,
                            "username", finalUsername,
                            "isTyping", isTyping));
        });
    }

    // ============================================
    // HELPERS
    // ============================================

    private void publishToOther(Long conversationId, User sender, MessageDTO msg) {
        try {
            conversationRepository.findById(conversationId).ifPresent(conv -> {
                Long otherUserId = conv.getUser1().getId().equals(sender.getId())
                        ? conv.getUser2().getId() : conv.getUser1().getId();
                redisPubSubService.publish("/queue/messages/" + otherUserId, msg);
            });
        } catch (Exception e) {
            System.err.println("⚠️ Errore publish: " + e.getMessage());
        }
    }

    private void markReadAndNotify(Long conversationId, Long userId) {
        messagingService.markAsRead(conversationId, userId);
        try {
            conversationRepository.findById(conversationId).ifPresent(conv -> {
                Long otherUserId = conv.getUser1().getId().equals(userId)
                        ? conv.getUser2().getId() : conv.getUser1().getId();
                redisPubSubService.publish("/queue/read-receipt/" + otherUserId,
                        Map.of("conversationId", conversationId, "readBy", userId));
            });
        } catch (Exception e) {
            System.err.println("⚠️ Errore read receipt: " + e.getMessage());
        }
    }
}