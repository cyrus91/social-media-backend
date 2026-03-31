package com.social.backend.components.ai.controller;

import com.social.backend.components.ai.dto.AICaptionRequest;
import com.social.backend.components.ai.dto.AIResponse;
import com.social.backend.components.ai.service.AIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Assistant", description = "AI-powered content generation")
public class AIController {

    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/generate-caption")
    @Operation(summary = "Genera caption per post")
    public ResponseEntity<AIResponse> generateCaption(@RequestBody AICaptionRequest request) {
        String caption = aiService.generateCaption(
                request.getPartialText(),
                request.getImageUrls(),
                request.getTone()
        );

        return ResponseEntity.ok(new AIResponse(caption, "llama3.2"));
    }

    @PostMapping("/improve-text")
    @Operation(summary = "Migliora testo esistente")
    public ResponseEntity<AIResponse> improveText(
            @RequestParam String text,
            @RequestParam(required = false, defaultValue = "social media post") String context) {

        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("📥 CONTROLLER - Request received");
        System.out.println("   text: " + text);
        System.out.println("   context: " + context);

        String improved = aiService.improveText(text, context);

        System.out.println("📤 CONTROLLER - Response from AIService");
        System.out.println("   improved: " + improved);

        AIResponse response = new AIResponse(improved, "llama3.2");

        System.out.println("📦 CONTROLLER - Creating AIResponse");
        System.out.println("   response: " + response);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/suggest-reply")
    @Operation(summary = "Suggerisci risposta a commento")
    public ResponseEntity<AIResponse> suggestReply(
            @RequestParam String comment,
            @RequestParam String postContext) {

        String reply = aiService.suggestReply(comment, postContext);
        return ResponseEntity.ok(new AIResponse(reply, "llama3.2"));
    }

    @PostMapping("/generate-caption-vision")
    @Operation(summary = "Genera caption AI analizzando le immagini (vision)")
    public ResponseEntity<AIResponse> generateCaptionVision(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> base64Images = (List<String>) body.getOrDefault("images", List.of());
        String tone = (String) body.getOrDefault("tone", "friendly");
        String partialText = (String) body.getOrDefault("partialText", "");
        String caption = aiService.generateCaptionVision(base64Images, tone, partialText);
        return ResponseEntity.ok(new AIResponse(caption, "llama-3.2-11b-vision-preview"));
    }

    @PostMapping("/generate")
    @Operation(summary = "Genera risposta AI generica")
    public ResponseEntity<Map<String, String>> generate(@RequestBody Map<String, String> body) {
        String result = aiService.generate(body.get("prompt"));
        return ResponseEntity.ok(Map.of("result", result));
    }

    @PostMapping("/suggest-hashtags")
    @Operation(summary = "Suggerisci hashtag")
    public ResponseEntity<List<String>> suggestHashtags(@RequestParam String content) {
        List<String> hashtags = aiService.suggestHashtags(content);
        return ResponseEntity.ok(hashtags);
    }
}