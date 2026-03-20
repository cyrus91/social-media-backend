package com.social.backend.components.email.service.impl;

import com.social.backend.components.email.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class EmailServiceImpl implements EmailService {

    private final WebClient webClient;

    @Value("${brevo.api-key}")
    private String apiKey;

    @Value("${brevo.from-email:noreply@socialapp.com}")
    private String fromEmail;

    @Value("${brevo.from-name:Social App}")
    private String fromName;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public EmailServiceImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.brevo.com/v3")
                .build();
    }

    @Override
    public void sendVerificationEmail(String toEmail, String username, String verificationToken) {
        String verificationLink = frontendUrl + "/verify-email?token=" + verificationToken;

        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #4F46E5;">Benvenuto su Social App, %s! 👋</h2>
                    <p style="color: #374151; font-size: 16px;">
                        Grazie per esserti registrato! Per completare la registrazione e accedere al tuo account,
                        devi confermare il tuo indirizzo email.
                    </p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background: linear-gradient(to right, #4F46E5, #7C3AED);
                                  color: white;
                                  padding: 14px 28px;
                                  text-decoration: none;
                                  border-radius: 8px;
                                  font-size: 16px;
                                  font-weight: bold;">
                            ✉️ Verifica Email
                        </a>
                    </div>
                    <p style="color: #6B7280; font-size: 14px;">
                        Il link scade tra <strong>24 ore</strong>.<br>
                        Se non hai creato un account, ignora questa email.
                    </p>
                    <hr style="border: none; border-top: 1px solid #E5E7EB; margin: 20px 0;">
                    <p style="color: #9CA3AF; font-size: 12px; text-align: center;">Social App</p>
                </div>
                """.formatted(username, verificationLink);

        Map<String, Object> emailRequest = Map.of(
                "sender", Map.of("name", fromName, "email", fromEmail),
                "to", List.of(Map.of("email", toEmail, "name", username)),
                "subject", "Verifica il tuo account Social App",
                "htmlContent", htmlContent
        );

        try {
            webClient.post()
                    .uri("/smtp/email")
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(emailRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            System.out.println("✅ Email di verifica inviata a: " + toEmail);
        } catch (Exception e) {
            System.err.println("❌ Errore invio email a " + toEmail + ": " + e.getMessage());
            throw new RuntimeException("Errore nell'invio dell'email di verifica");
        }
    }
}