package com.social.backend.components.ai.service;

import java.util.List;

public interface AIService {
    String generate(String prompt);

    String generateCaption(String partialText, List<String> imageUrls, String tone);

    String improveText(String text, String context);

    String suggestReply(String originalComment, String postContext);

    List<String> suggestHashtags(String content);
}