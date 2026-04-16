package com.social.backend.components.email.service.impl;

import com.social.backend.components.email.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.List;

@Service
@Profile("!test")
public class EmailServiceImpl implements EmailService {

    private final WebClient webClient;

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${resend.from-email:noreply@cirodattilo-app.site}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public EmailServiceImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.resend.com")
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
                "from", fromEmail,
                "to", List.of(toEmail),
                "subject", "Verifica il tuo account Social App",
                "html", htmlContent
        );

        try {
            webClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
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

    @Override
    public void sendPasswordResetEmail(String toEmail, String username, String resetToken) {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; background: #09090f; border-radius: 12px;">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <h1 style="font-size: 32px; font-weight: 800; background: linear-gradient(135deg, #7C3AED, #06B6D4); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; margin: 0;">Nexus</h1>
                    </div>
                    <h2 style="color: #e8e6ff; font-size: 20px;">Reset della password, %s</h2>
                    <p style="color: #8b82b0; font-size: 15px; line-height: 1.6;">
                        Hai richiesto il reset della tua password. Clicca il bottone qui sotto per sceglierne una nuova.
                    </p>
                    <div style="text-align: center; margin: 32px 0;">
                        <a href="%s"
                           style="background: linear-gradient(135deg, #5B21B6, #7C3AED);
                                  color: white;
                                  padding: 14px 32px;
                                  text-decoration: none;
                                  border-radius: 999px;
                                  font-size: 15px;
                                  font-weight: 700;">
                            🔑 Reimposta Password
                        </a>
                    </div>
                    <p style="color: #5a5278; font-size: 13px;">
                        Il link scade tra <strong style="color: #8b82b0;">1 ora</strong>.<br>
                        Se non hai richiesto il reset, ignora questa email.
                    </p>
                    <hr style="border: none; border-top: 1px solid rgba(124,58,237,0.15); margin: 24px 0;">
                    <p style="color: #4a4768; font-size: 12px; text-align: center;">Nexus — Where connections come alive</p>
                </div>
                """.formatted(username, resetLink);

        Map<String, Object> emailRequest = Map.of(
                "from", fromEmail,
                "to", List.of(toEmail),
                "subject", "Reset password Nexus",
                "html", htmlContent
        );

        try {
            webClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(emailRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            System.out.println("✅ Email reset password inviata a: " + toEmail);
        } catch (Exception e) {
            System.err.println("❌ Errore invio email reset a " + toEmail + ": " + e.getMessage());
            throw new RuntimeException("Errore nell'invio dell'email di reset");
        }
    }
}