package com.social.backend.components.messaging.entity;

import com.social.backend.components.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "messages",
        indexes = {
                @Index(name = "idx_messages_conversation", columnList = "conversation_id"),
                @Index(name = "idx_messages_sender", columnList = "sender_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    // Reply: id del messaggio a cui si risponde
    @Column(name = "reply_to_id")
    private Long replyToId;

    // Testo del messaggio originale (snapshot per mostrarlo anche se cancellato)
    @Column(name = "reply_to_content", columnDefinition = "TEXT")
    private String replyToContent;

    @Column(name = "reply_to_sender_username", length = 50)
    private String replyToSenderUsername;

    // Cancellazione
    @Column(name = "deleted_for_all", nullable = false)
    @Builder.Default
    private boolean deletedForAll = false;

    // Messaggi che scadono
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Nessun EAGER — le reactions vengono caricate esplicitamente con findByIdWithReactions
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MessageReaction> reactions = new ArrayList<>();
}