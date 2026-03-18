package com.social.backend.components.auth.service;

import com.social.backend.components.auth.dto.LoginRequest;
import com.social.backend.components.auth.dto.LoginResponse;
import com.social.backend.components.auth.dto.RegisterRequest;
import com.social.backend.components.auth.dto.TokenRefreshResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse register(RegisterRequest request);

    TokenRefreshResponse refreshToken(String refreshToken);

    void logout(Long userId);
}