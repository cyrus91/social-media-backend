package com.social.backend.config;

import com.social.backend.components.email.service.EmailService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import static org.mockito.Mockito.mock;

/**
 * Configurazione EmailService per il profilo "test".
 * Fornisce un bean no-op che non chiama l'API Resend reale,
 * evitando il 500 "Errore nell'invio dell'email di verifica" nei test.
 *
 * EmailServiceImpl ha @Profile("!test") quindi non crea conflitti.
 */
@Configuration
@Profile("test")
public class TestEmailConfig {

    @Bean
    public EmailService emailService() {
        // Mock no-op: sendVerificationEmail e sendPasswordResetEmail non fanno nulla
        return mock(EmailService.class);
    }
}