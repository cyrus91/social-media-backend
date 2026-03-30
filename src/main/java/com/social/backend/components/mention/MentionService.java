package com.social.backend.components.mention;

import com.social.backend.components.notification.enums.NotificationType;
import com.social.backend.components.notification.service.NotificationService;
import com.social.backend.components.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servizio che estrae le mention (@username) da un testo
 * e invia notifiche agli utenti taggati.
 */
@Service
public class MentionService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w.]+)");

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public MentionService(UserRepository userRepository,
                          NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * Estrae gli username taggati nel testo e invia notifica a ciascuno.
     *
     * @param content   testo del post/commento
     * @param actorId   chi ha scritto
     * @param postId    post di riferimento
     * @param commentId commento di riferimento (null se post)
     * @param actorUsername username di chi scrive (per il messaggio)
     */
    public void processMentions(String content, Long actorId,
                                Long postId, Long commentId,
                                String actorUsername) {
        if (content == null || content.isBlank()) return;

        Set<String> mentioned = extractMentions(content);
        for (String username : mentioned) {
            userRepository.findByUsername(username).ifPresent(user -> {
                // Non notificare se l'autore si tagga da solo
                if (user.getId().equals(actorId)) return;
                notificationService.createNotification(
                        user.getId(), actorId,
                        NotificationType.MENTION, postId, commentId,
                        actorUsername + " ti ha menzionato");
            });
        }
    }

    /** Estrae tutti gli @username unici dal testo */
    public Set<String> extractMentions(String text) {
        Set<String> mentions = new HashSet<>();
        Matcher m = MENTION_PATTERN.matcher(text);
        while (m.find()) {
            mentions.add(m.group(1).toLowerCase());
        }
        return mentions;
    }
}