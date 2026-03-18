package com.social.backend.components.post.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePostRequest {

    private String content;

    @Size(max = 255)
    private String imageUrl;
}
