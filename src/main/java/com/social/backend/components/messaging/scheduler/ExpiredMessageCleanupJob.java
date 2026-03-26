package com.social.backend.components.messaging.scheduler;

import com.social.backend.components.messaging.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job schedulato che elimina i messaggi scaduti (expiresAt < NOW).
 * Gira ogni minuto — i messaggi vengono marcati come deletedForAll.
 */
@Component
public class ExpiredMessageCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredMessageCleanupJob.class);
    private final MessageRepository messageRepository;

    public ExpiredMessageCleanupJob(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Scheduled(fixedDelay = 60_000) // ogni minuto
    @Transactional
    public void cleanupExpiredMessages() {
        int count = messageRepository.markExpiredAsDeleted();
        if (count > 0) {
            log.info("🗑️ {} messaggi scaduti eliminati", count);
        }
    }
}