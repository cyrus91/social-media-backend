package com.social.backend.components.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.backend.components.auth.dto.LoginRequest;
import com.social.backend.components.auth.dto.LoginResponse;
import com.social.backend.components.auth.dto.RegisterRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AuthController Integration Tests")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Pulisci database prima di ogni test
        userRepository.deleteAll();

        // Crea utente di test
        testUser = User.builder()
                .username("mario_rossi")
                .email("mario@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .bio("Sviluppatore Java")
                .emailVerified(true)
                .build();

        userRepository.save(testUser);
    }

    @Test
    @DisplayName("POST /api/auth/register - Dovrebbe registrare un nuovo utente con successo")
    void shouldRegisterNewUserSuccessfully() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername("nuovo_utente");
        request.setEmail("nuovo@example.com");
        request.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("pending_verification"))
                .andExpect(jsonPath("$.token").isEmpty())
                .andExpect(jsonPath("$.user.username").value("nuovo_utente"))
                .andExpect(jsonPath("$.user.email").value("nuovo@example.com"));

        // Verifica che l'utente sia stato salvato nel database
        assertThat(userRepository.findByUsername("nuovo_utente")).isPresent();
    }

    @Test
    @DisplayName("POST /api/auth/register - Dovrebbe fallire se username già esiste")
    void shouldFailRegistrationWhenUsernameExists() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername("mario_rossi"); // Username già esistente
        request.setEmail("new@example.com");
        request.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Username già esistente: mario_rossi"));
    }

    @Test
    @DisplayName("POST /api/auth/register - Dovrebbe fallire se email già esiste")
    void shouldFailRegistrationWhenEmailExists() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername("nuovo_utente");
        request.setEmail("mario@example.com"); // Email già esistente
        request.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email già esistente: mario@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/register - Dovrebbe fallire con dati non validi")
    void shouldFailRegistrationWithInvalidData() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername(""); // Username vuoto
        request.setEmail("invalid-email"); // Email non valida
        request.setPassword("123"); // Password troppo corta

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/auth/login - Dovrebbe fare login con successo")
    void shouldLoginSuccessfully() throws Exception {
        // Given
        LoginRequest request = new LoginRequest();
        request.setUsername("mario_rossi");
        request.setPassword("password123");

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("mario_rossi"))
                .andExpect(jsonPath("$.user.email").value("mario@example.com"))
                .andReturn();

        // Verifica che il token JWT sia valido (ha 3 parti separate da punti)
        String responseBody = result.getResponse().getContentAsString();
        LoginResponse response = objectMapper.readValue(responseBody, LoginResponse.class);
        String token = response.getToken();

        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3); // JWT ha 3 parti: header.payload.signature
    }

    @Test
    @DisplayName("POST /api/auth/login - Dovrebbe fallire con username sbagliato")
    void shouldFailLoginWithWrongUsername() throws Exception {
        // Given
        LoginRequest request = new LoginRequest();
        request.setUsername("utente_inesistente");
        request.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Dovrebbe fallire con password sbagliata")
    void shouldFailLoginWithWrongPassword() throws Exception {
        // Given
        LoginRequest request = new LoginRequest();
        request.setUsername("mario_rossi");
        request.setPassword("password_sbagliata");

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Dovrebbe fallire con dati mancanti")
    void shouldFailLoginWithMissingData() throws Exception {
        // Given
        LoginRequest request = new LoginRequest();
        // Username e password mancanti

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Dovrebbe hashare la password durante la registrazione")
    void shouldHashPasswordDuringRegistration() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername("test_hash");
        request.setEmail("test@example.com");
        request.setPassword("plainPassword123");

        // When
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Then - Verifica che la password sia stata hashata
        User savedUser = userRepository.findByUsername("test_hash").orElseThrow();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("plainPassword123");
        assertThat(savedUser.getPasswordHash()).startsWith("$2a$"); // BCrypt hash prefix
        assertThat(passwordEncoder.matches("plainPassword123", savedUser.getPasswordHash())).isTrue();
    }
}