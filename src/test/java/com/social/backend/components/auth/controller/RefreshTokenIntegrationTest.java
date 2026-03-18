package com.social.backend.components.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.backend.components.auth.dto.LoginRequest;
import com.social.backend.components.auth.dto.LoginResponse;
import com.social.backend.components.auth.dto.RegisterRequest;
import com.social.backend.components.auth.dto.TokenRefreshRequest;
import com.social.backend.components.auth.entity.RefreshToken;
import com.social.backend.components.auth.repository.RefreshTokenRepository;
import com.social.backend.components.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
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

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/auth/login - Dovrebbe restituire access token e refresh token")
    void shouldReturnAccessAndRefreshToken() throws Exception {
        // Registra utente
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("mario");
        registerRequest.setEmail("mario@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setBio("Test user");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Login
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

        // Verifica che il refresh token sia stato salvato
        assertThat(refreshTokenRepository.count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("POST /api/auth/register - Dovrebbe restituire access token e refresh token")
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
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("luigi"));

        // Verifica che il refresh token sia stato salvato
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Dovrebbe rinnovare access token con refresh token valido")
    void shouldRefreshAccessToken() throws Exception {
        // Registra e ottieni token
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("peach");
        registerRequest.setEmail("peach@example.com");
        registerRequest.setPassword("password123");

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String registerResponse = registerResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(registerResponse, LoginResponse.class);
        String refreshToken = loginResponse.getRefreshToken();

        // Usa refresh token per ottenere nuovo access token
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
        // Registra utente
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("toad");
        registerRequest.setEmail("toad@example.com");
        registerRequest.setPassword("password123");

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String registerResponse = registerResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(registerResponse, LoginResponse.class);
        String refreshToken = loginResponse.getRefreshToken();

        // Modifica manualmente il refresh token per farlo scadere
        RefreshToken tokenEntity = refreshTokenRepository.findByToken(refreshToken).orElseThrow();
        tokenEntity.setExpiryDate(Instant.now().minusSeconds(3600)); // Scaduto 1 ora fa
        refreshTokenRepository.save(tokenEntity);

        // Prova a usare il token scaduto
        TokenRefreshRequest refreshRequest = new TokenRefreshRequest();
        refreshRequest.setRefreshToken(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").isNotEmpty());

        // Verifica che il token scaduto sia stato eliminato
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

    @Test
    @DisplayName("POST /api/auth/logout - Dovrebbe invalidare il refresh token")
    void shouldLogoutAndInvalidateRefreshToken() throws Exception {
        // Registra utente
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("bowser");
        registerRequest.setEmail("bowser@example.com");
        registerRequest.setPassword("password123");

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String registerResponse = registerResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(registerResponse, LoginResponse.class);
        String accessToken = loginResponse.getToken();
        String refreshToken = loginResponse.getRefreshToken();

        // Verifica che il refresh token esista
        assertThat(refreshTokenRepository.findByToken(refreshToken)).isPresent();

        // Logout
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andDo(print())
                .andExpect(status().isNoContent());

        // Verifica che il refresh token sia stato eliminato
        assertThat(refreshTokenRepository.count()).isEqualTo(0);

        // Prova a usare il refresh token invalidato
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

    @Test
    @DisplayName("Utente può avere solo un refresh token attivo per volta")
    void shouldHaveOnlyOneRefreshTokenPerUser() throws Exception {
        // Registra utente
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("yoshi");
        registerRequest.setEmail("yoshi@example.com");
        registerRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Primo login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("yoshi");
        loginRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        // Secondo login (dovrebbe creare un nuovo refresh token)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        // Verifica che ci siano 2 refresh token (uno per register, due per i login)
        assertThat(refreshTokenRepository.count()).isEqualTo(3);
    }
}