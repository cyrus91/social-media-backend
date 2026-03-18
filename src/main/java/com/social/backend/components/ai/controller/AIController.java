package com.social.backend.components.ai.controller;

import com.social.backend.components.ai.dto.AICaptionRequest;
import com.social.backend.components.ai.dto.AIResponse;
import com.social.backend.components.ai.service.AIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/suggest-hashtags")
    @Operation(summary = "Suggerisci hashtag")
    public ResponseEntity<List<String>> suggestHashtags(@RequestParam String content) {
        List<String> hashtags = aiService.suggestHashtags(content);
        return ResponseEntity.ok(hashtags);
    }
}