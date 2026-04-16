package com.social.backend.components.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.backend.components.auth.dto.LoginRequest;
import com.social.backend.components.auth.dto.LoginResponse;
import com.social.backend.components.auth.dto.RegisterRequest;
import com.social.backend.components.auth.dto.TokenRefreshRequest;
import com.social.backend.components.auth.entity.RefreshToken;
import com.social.backend.components.auth.repository.RefreshTokenRepository;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Refresh Token Integration Tests")
class RefreshTokenIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        // Crea utente direttamente nel DB con emailVerified=true.
        // Il flusso register->emailVerify è bypassato nei test: il focus
        // è sul comportamento del refresh token, non sulla verifica email.
        User mario = User.builder()
                .username("mario")
                .email("mario@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .build();
        userRepository.save(mario);
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private LoginResponse loginAndGetResponse(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);
    }

    // ─── Test register ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register - Dovrebbe restituire pending_verification (email non ancora verificata)")
    void shouldReturnTokensOnRegister() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("luigi");
        registerRequest.setEmail("luigi@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setBio("Test user");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("pending_verification"))
                .andExpect(jsonPath("$.token").isEmpty())
                .andExpect(jsonPath("$.user.username").value("luigi"));
    }

    // ─── Test login ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login - Dovrebbe restituire access token e refresh token")
    void shouldReturnAccessAndRefreshToken() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("mario");
        loginRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("mario"));

        assertThat(refreshTokenRepository.count()).isGreaterThan(0);
    }

    // ─── Test /api/auth/refresh ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/refresh - Dovrebbe rinnovare access token con refresh token valido")
    void shouldRefreshAccessToken() throws Exception {
        LoginResponse loginResponse = loginAndGetResponse("mario", "password123");
        String refreshToken = loginResponse.getRefreshToken();

        TokenRefreshRequest refreshRequest = new TokenRefreshRequest();
        refreshRequest.setRefreshToken(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value(refreshToken))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Dovrebbe fallire con refresh token inesistente")
    void shouldFailRefreshWithInvalidToken() throws Exception {
        TokenRefreshRequest refreshRequest = new TokenRefreshRequest();
        refreshRequest.setRefreshToken("token-inesistente-123");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Dovrebbe fallire con refresh token scaduto")
    void shouldFailRefreshWithExpiredToken() throws Exception {
        LoginResponse loginResponse = loginAndGetResponse("mario", "password123");
        String refreshToken = loginResponse.getRefreshToken();

        // Scade manualmente il token
        RefreshToken tokenEntity = refreshTokenRepository.findByToken(refreshToken).orElseThrow();
        tokenEntity.setExpiryDate(Instant.now().minusSeconds(3600));
        refreshTokenRepository.save(tokenEntity);

        TokenRefreshRequest refreshRequest = new TokenRefreshRequest();
        refreshRequest.setRefreshToken(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(refreshTokenRepository.findByToken(refreshToken)).isEmpty();
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Dovrebbe fallire con refresh token vuoto")
    void shouldFailRefreshWithEmptyToken() throws Exception {
        TokenRefreshRequest refreshRequest = new TokenRefreshRequest();
        refreshRequest.setRefreshToken("");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    // ─── Test /api/auth/logout ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/logout - Dovrebbe invalidare il refresh token")
    void shouldLogoutAndInvalidateRefreshToken() throws Exception {
        LoginResponse loginResponse = loginAndGetResponse("mario", "password123");
        String accessToken = loginResponse.getToken();
        String refreshToken = loginResponse.getRefreshToken();

        assertThat(refreshTokenRepository.findByToken(refreshToken)).isPresent();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andDo(print())
                .andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.count()).isEqualTo(0);

        TokenRefreshRequest refreshRequest = new TokenRefreshRequest();
        refreshRequest.setRefreshToken(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/auth/logout - Dovrebbe fallire senza autenticazione")
    void shouldFailLogoutWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    // ─── Test multi-login ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Login multipli generano refresh token separati")
    void shouldHaveOnlyOneRefreshTokenPerUser() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("mario");
        loginRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        // Due login → due refresh token
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }
}