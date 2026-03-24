package com.social.backend.components.messaging.service;

import com.social.backend.components.messaging.dto.ConversationDTO;
import com.social.backend.components.messaging.dto.MessageDTO;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MessagingService {

    // Ottieni o crea conversazione tra due utenti
    ConversationDTO getOrCreateConversation(Long userId, Long otherUserId);

    // Lista conversazioni di un utente
    List<ConversationDTO> getConversations(Long userId);

    // Messaggi di una conversazione (paginati)
    Page<MessageDTO> getMessages(Long conversationId, Long userId, int page, int size);

    // Invia messaggio testuale
    MessageDTO sendMessage(Long conversationId, Long senderId, String content);

    // Invia messaggio con immagine
    MessageDTO sendMessageWithImage(Long conversationId, Long senderId, String content, MultipartFile image);

    // Segna messaggi come letti
    void markAsRead(Long conversationId, Long userId);

    // Conta messaggi non letti totali
    long countUnread(Long userId);
}