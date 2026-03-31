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
@ConditionalOnProperty(name = "ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaAIServiceImpl implements AIService {

    private final WebClient webClient;

    @Value("${ai.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${ai.ollama.model:llama3.2}")
    private String model;

    public OllamaAIServiceImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String generateCaptionVision(List<String> base64Images, String tone, String partialText) {
        // Ollama non supporta vision — fallback al metodo testuale
        return generateCaption(partialText != null ? partialText : "", null, tone);
    }

    @Override
    public String generate(String prompt) {
        return generateCaption(prompt, null, null);
    }

    @Override
    public String generateCaption(String partialText, List<String> imageUrls, String tone) {
        String prompt = buildCaptionPrompt(partialText, imageUrls, tone);
        return callOllama(prompt);
    }

    @Override
    public String improveText(String text, String context) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("🔧 SERVICE - improveText called");
        System.out.println("   text: " + text);
        System.out.println("   context: " + context);

        String prompt = String.format(
                "Migliora questo testo per un post social, mantieni il significato ma rendilo più coinvolgente:\n\n" +
                        "Testo: %s\n\n" +
                        "Contesto: %s\n\n" +
                        "Rispondi SOLO con il testo migliorato, senza introduzioni.",
                text, context
        );

        System.out.println("📝 SERVICE - Calling Ollama with prompt");
        String result = callOllama(prompt);

        System.out.println("✅ SERVICE - Ollama returned: " + result);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return result;
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
        return callOllama(prompt);
    }

    @Override
    public List<String> suggestHashtags(String content) {
        String prompt = String.format(
                "Genera 5 hashtag rilevanti per questo contenuto:\n\n%s\n\n" +
                        "Rispondi SOLO con gli hashtag separati da virgola, senza spiegazioni.",
                content
        );
        String response = callOllama(prompt);

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
            prompt.append("Testo iniziale: ").append(partialText).append("\n\n");
        }

        if (imageUrls != null && !imageUrls.isEmpty()) {
            prompt.append("Il post contiene ").append(imageUrls.size()).append(" immagini.\n");
        }

        prompt.append("Tono desiderato: ").append(tone).append("\n\n");
        prompt.append("Rispondi SOLO con la caption, senza introduzioni o spiegazioni.\n");
        prompt.append("Mantieni lunghezza 50-150 caratteri.");

        return prompt.toString();
    }

    private String callOllama(String prompt) {
        try {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", 0.7,
                            "top_p", 0.9,
                            "num_predict", 200
                    )
            );

            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("🤖 Ollama Request: " + model);
            System.out.println("📝 Prompt: " + prompt.substring(0, Math.min(100, prompt.length())) + "...");
            System.out.println("🌐 URL: " + ollamaUrl + "/api/generate");
            System.out.println("⏰ Timeout: 120 seconds");

            long startTime = System.currentTimeMillis();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri(ollamaUrl + "/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();

            long endTime = System.currentTimeMillis();
            System.out.println("⏱️ Ollama responded in: " + (endTime - startTime) + "ms");

            String generatedText = (String) response.get("response");
            System.out.println("✅ Ollama Response: " + generatedText);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            return generatedText != null ? generatedText.trim() : "Errore nella generazione";

        } catch (Exception e) {
            System.err.println("❌ Errore chiamata Ollama: " + e.getMessage());
            e.printStackTrace();
            return "Servizio AI temporaneamente non disponibile. Riprova tra poco.";
        }
    }
}