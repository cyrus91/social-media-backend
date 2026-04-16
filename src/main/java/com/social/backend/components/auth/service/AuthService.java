package com.social.backend.components.auth.service;

import com.social.backend.components.auth.dto.LoginRequest;
import com.social.backend.components.auth.dto.LoginResponse;
import com.social.backend.components.auth.dto.RegisterRequest;
import com.social.backend.components.auth.dto.TokenRefreshResponse;

public interface AuthService {

    /**
     * Scambia il codice temporaneo OAuth2 (arrivato tramite redirect URL)
     * con i token reali (JWT + refresh). Il codice viene eliminato da Redis
     * dopo il primo utilizzo (one-time use).
     */
    LoginResponse exchangeOAuth2Code(String code);
    LoginResponse login(LoginRequest request);

    LoginResponse register(RegisterRequest request);

    TokenRefreshResponse refreshToken(String refreshToken);

    void logout(Long userId);

    void verifyEmail(String token);

    void resendVerification(String email);

    void requestPasswordReset(String email);

    void resetPassword(String token, String newPassword);

    void changePassword(Long userId, String currentPassword, String newPassword);
}