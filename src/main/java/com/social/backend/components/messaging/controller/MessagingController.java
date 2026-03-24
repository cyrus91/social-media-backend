package com.social.backend.components.messaging.controller;

import com.social.backend.components.messaging.dto.ConversationDTO;
import com.social.backend.components.messaging.dto.MessageDTO;
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

    public MessagingController(MessagingService messagingService,
                               RedisPubSubService redisPubSubService) {
        this.messagingService = messagingService;
        this.redisPubSubService = redisPubSubService;
    }

    // GET tutte le conversazioni dell'utente loggato
    @GetMapping("/conversations")
    public List<ConversationDTO> getConversations(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return messagingService.getConversations(userDetails.getUser().getId());
    }

    // GET o crea conversazione con un utente specifico
    @PostMapping("/conversations/{otherUserId}")
    public ConversationDTO getOrCreateConversation(
            @PathVariable Long otherUserId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return messagingService.getOrCreateConversation(
                userDetails.getUser().getId(), otherUserId);
    }

    // GET messaggi di una conversazione (segna anche come letti)
    @GetMapping("/conversations/{conversationId}/messages")
    public Page<MessageDTO> getMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Page<MessageDTO> messages = messagingService.getMessages(
                conversationId, userDetails.getUser().getId(), page, size);
        messagingService.markAsRead(conversationId, userDetails.getUser().getId());
        return messages;
    }

    // POST invia messaggio testuale
    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDTO sendMessage(
            @PathVariable Long conversationId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        MessageDTO msg = messagingService.sendMessage(
                conversationId, userDetails.getUser().getId(), body.get("content"));
        Long otherUserId = getOtherUserId(conversationId, userDetails.getUser());
        redisPubSubService.publish("/queue/messages/" + otherUserId, msg);
        return msg;
    }

    // POST invia messaggio con immagine
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
        Long otherUserId = getOtherUserId(conversationId, userDetails.getUser());
        redisPubSubService.publish("/queue/messages/" + otherUserId, msg);
        return msg;
    }

    // GET conteggio messaggi non letti
    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return Map.of("count", messagingService.countUnread(userDetails.getUser().getId()));
    }

    // PUT segna conversazione come letta
    @PutMapping("/conversations/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        messagingService.markAsRead(conversationId, userDetails.getUser().getId());
    }

    private Long getOtherUserId(Long conversationId, User currentUser) {
        return messagingService.getConversations(currentUser.getId()).stream()
                .filter(c -> c.getId().equals(conversationId))
                .findFirst()
                .map(ConversationDTO::getOtherUserId)
                .orElse(0L);
    }
}