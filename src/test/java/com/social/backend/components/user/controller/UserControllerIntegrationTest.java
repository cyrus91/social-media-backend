package com.social.backend.components.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.backend.components.auth.dto.LoginRequest;
import com.social.backend.components.auth.dto.LoginResponse;
import com.social.backend.components.user.dto.UpdateUserRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("UserController Integration Tests")
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() throws Exception {
        // Pulisci database
        userRepository.deleteAll();

        // Crea utente di test
        testUser = User.builder()
                .username("mario_rossi")
                .email("mario@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .bio("Sviluppatore Java")
                .avatarUrl("https://example.com/avatar.jpg")
                .build();
        userRepository.save(testUser);

        // Ottieni JWT token
        jwtToken = getJwtToken("mario_rossi", "password123");
    }

    // Helper method per ottenere JWT token
    private String getJwtToken(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(responseBody, LoginResponse.class);
        return loginResponse.getToken();
    }

    @Test
    @DisplayName("GET /api/users/me - Dovrebbe ottenere profilo utente corrente")
    void shouldGetCurrentUserProfile() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + jwtToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUser.getId()))
                .andExpect(jsonPath("$.username").value("mario_rossi"))
                .andExpect(jsonPath("$.email").value("mario@example.com"))
                .andExpect(jsonPath("$.bio").value("Sviluppatore Java"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.com/avatar.jpg"));
    }

    @Test
    @DisplayName("GET /api/users/me - Dovrebbe fallire senza token JWT")
    void shouldFailGetCurrentUserWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /api/users/me - Dovrebbe fallire con token JWT non valido")
    void shouldFailGetCurrentUserWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/users/id/{id} - Dovrebbe ottenere profilo utente per ID")
    void shouldGetUserById() throws Exception {
        mockMvc.perform(get("/api/users/id/" + testUser.getId())  // ✅ Corretto: /id/{id}
                        .header("Authorization", "Bearer " + jwtToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUser.getId()))
                .andExpect(jsonPath("$.username").value("mario_rossi"))
                .andExpect(jsonPath("$.email").value("mario@example.com"));
    }

    @Test
    @DisplayName("GET /api/users/id/{id} - Dovrebbe fallire se utente non esiste")
    void shouldFailGetUserByIdWhenNotFound() throws Exception {
        mockMvc.perform(get("/api/users/id/99999")  // ✅ Corretto: /id/99999
                        .header("Authorization", "Bearer " + jwtToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Utente non trovato con ID: 99999"));
    }

    @Test
    @DisplayName("GET /api/users/{username} - Dovrebbe ottenere profilo per username")
    void shouldGetUserByUsername() throws Exception {
        mockMvc.perform(get("/api/users/mario_rossi")  // ✅ Corretto: /{username}
                        .header("Authorization", "Bearer " + jwtToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("mario_rossi"))
                .andExpect(jsonPath("$.email").value("mario@example.com"));
    }

    @Test
    @DisplayName("GET /api/users/{username} - Dovrebbe fallire se username non esiste")
    void shouldFailGetUserByUsernameWhenNotFound() throws Exception {
        mockMvc.perform(get("/api/users/utente_inesistente")
                        .header("Authorization", "Bearer " + jwtToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Utente non trovato con username: utente_inesistente"));
    }

    @Test
    @DisplayName("PUT /api/users/me - Dovrebbe aggiornare profilo utente corrente")
    void shouldUpdateCurrentUser() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("mario.new@example.com");
        request.setBio("Bio aggiornata");
        request.setAvatarUrl("https://example.com/new-avatar.jpg");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("mario.new@example.com"))
                .andExpect(jsonPath("$.bio").value("Bio aggiornata"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.com/new-avatar.jpg"));

        // Verifica che i dati siano stati aggiornati nel database
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getEmail()).isEqualTo("mario.new@example.com");
        assertThat(updatedUser.getBio()).isEqualTo("Bio aggiornata");
    }

    @Test
    @DisplayName("PUT /api/users/me - Dovrebbe aggiornare password")
    void shouldUpdatePassword() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setNewPassword("newPassword456");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk());

        // Verifica che la password sia stata aggiornata e hashata
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("newPassword456", updatedUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("password123", updatedUser.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("PUT /api/users/me - Dovrebbe fallire con email già in uso")
    void shouldFailUpdateWithDuplicateEmail() throws Exception {
        // Crea un altro utente
        User otherUser = User.builder()
                .username("altro_utente")
                .email("altro@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .build();
        userRepository.save(otherUser);

        // Prova ad aggiornare con email dell'altro utente
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("altro@example.com");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email già in uso: altro@example.com"));
    }

    @Test
    @DisplayName("PUT /api/users/me - Dovrebbe fallire senza token JWT")
    void shouldFailUpdateWithoutToken() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setBio("Nuova bio");

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }
}