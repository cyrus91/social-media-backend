package com.social.backend.components.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenRefreshRequest {

    @NotBlank(message = "Il refresh token non può essere vuoto")
    private String refreshToken;
}