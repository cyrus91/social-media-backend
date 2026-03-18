package com.social.backend.components.ai.dto;

import lombok.Data;
import java.util.List;

@Data
public class AICaptionRequest {
    private String partialText;
    private List<String> imageUrls;
    private String tone = "friendly";
}