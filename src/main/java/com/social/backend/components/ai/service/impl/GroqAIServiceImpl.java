package com.social.backend.components.ai.service.impl;

import com.social.backend.components.ai.service.AIService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "groq")
public class GroqAIServiceImpl implements AIService {

    private final WebClient webClient;

    @Value("${ai.groq.api-key}")
    private String apiKey;

    @Value("${ai.groq.model:llama-3.1-8b-instant}")
    private String model;

    @Value("${ai.groq.url:https://api.groq.com/openai/v1}")
    private String groqUrl;

    public GroqAIServiceImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String generateCaption(String partialText, List<String> imageUrls, String tone) {
        String prompt = buildCaptionPrompt(partialText, imageUrls, tone);
        return callGroq(prompt, 150);
    }

    @Override
    public String improveText(String text, String context) {
        String prompt = String.format(
                "Migliora questo testo per un post social, mantieni il significato ma rendilo più coinvolgente e naturale:\n\n" +
                        "Testo: %s\n\n" +
                        "Contesto: %s\n\n" +
                        "Rispondi SOLO con il testo migliorato, senza introduzioni o spiegazioni.",
                text, context
        );
        return callGroq(prompt, 200);
    }

    @Override
    public String suggestReply(String originalComment, String postContext) {
        String prompt = String.format(
                "Genera una risposta cordiale e appropriata a questo commento:\n\n" +
                        "Commento: %s\n\n" +
                        "Contesto del post: %s\n\n" +
                        "Rispondi in modo naturale, amichevole e professionale. SOLO la risposta, nessuna introduzione.",
                originalComment, postContext
        );
        return callGroq(prompt, 150);
    }

    @Override
    public List<String> suggestHashtags(String content) {
        String prompt = String.format(
                "Genera 5 hashtag rilevanti per questo contenuto social:\n\n%s\n\n" +
                        "Rispondi SOLO con gli hashtag separati da virgola (senza il simbolo #), esempio: travel,sunset,beach,photography,nature",
                content
        );
        String response = callGroq(prompt, 50);

        return Arrays.stream(response.split(","))
                .map(String::trim)
                .filter(h -> !h.isEmpty())
                .map(h -> h.startsWith("#") ? h : "#" + h)
                .limit(5)
                .toList();
    }

    private String buildCaptionPrompt(String partialText, List<String> imageUrls, String tone) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Genera una caption coinvolgente per un post social media.\n\n");

        if (partialText != null && !partialText.isEmpty()) {
            prompt.append("Idea iniziale: ").append(partialText).append("\n\n");
        }

        if (imageUrls != null && !imageUrls.isEmpty()) {
            prompt.append("Il post contiene ").append(imageUrls.size()).append(" immagini.\n");
        }

        prompt.append("Tono desiderato: ").append(tone).append("\n\n");
        prompt.append("Rispondi SOLO con la caption (50-100 caratteri), senza introduzioni.");

        return prompt.toString();
    }

    private String callGroq(String prompt, int maxTokens) {
        try {
            Map<String, Object> message = Map.of(
                    "role", "user",
                    "content", prompt
            );

            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", List.of(message),
                    "temperature", 0.7,
                    "max_tokens", maxTokens,
                    "top_p", 0.9
            );

            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("🤖 Groq Request: " + model);
            System.out.println("📝 Prompt: " + prompt.substring(0, Math.min(100, prompt.length())) + "...");
            System.out.println("🌐 URL: " + groqUrl + "/chat/completions");

            long startTime = System.currentTimeMillis();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri(groqUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            long endTime = System.currentTimeMillis();
            System.out.println("⏱️ Groq responded in: " + (endTime - startTime) + "ms");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");

            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> firstChoice = choices.get(0);
                @SuppressWarnings("unchecked")
                Map<String, Object> message_response = (Map<String, Object>) firstChoice.get("message");
                String content = (String) message_response.get("content");

                System.out.println("✅ Groq Response: " + content);
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                return content != null ? content.trim() : "Errore nella generazione";
            }

            return "Nessuna risposta generata";

        } catch (Exception e) {
            System.err.println("❌ Errore chiamata Groq: " + e.getMessage());
            e.printStackTrace();
            return "Servizio AI temporaneamente non disponibile. Riprova tra poco.";
        }
    }
}