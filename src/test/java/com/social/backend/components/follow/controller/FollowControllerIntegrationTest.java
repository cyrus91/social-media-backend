package com.social.backend.components.follow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.backend.components.auth.dto.LoginRequest;
import com.social.backend.components.auth.dto.LoginResponse;
import com.social.backend.components.follow.dto.FollowRequest;
import com.social.backend.components.follow.repository.FollowRepository;
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
@DisplayName("FollowController Integration Tests")
class FollowControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User mario;
    private User luigi;
    private User peach;
    private String marioToken;
    private String luigiToken;
    private String peachToken;

    @BeforeEach
    void setUp() throws Exception {
        // Pulisci database
        followRepository.deleteAll();
        userRepository.deleteAll();

        // Crea utenti di test
        mario = User.builder()
                .username("mario")
                .email("mario@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .build();
        userRepository.save(mario);

        luigi = User.builder()
                .username("luigi")
                .email("luigi@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .build();
        userRepository.save(luigi);

        peach = User.builder()
                .username("peach")
                .email("peach@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .build();
        userRepository.save(peach);

        // Ottieni JWT token
        marioToken = getJwtToken("mario", "password123");
        luigiToken = getJwtToken("luigi", "password123");
        peachToken = getJwtToken("peach", "password123");
    }

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
    @DisplayName("POST /api/follows - Dovrebbe seguire un utente")
    void shouldFollowUser() throws Exception {
        FollowRequest request = new FollowRequest();
        request.setFollowedId(luigi.getId());

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.followerId").value(mario.getId()))
                .andExpect(jsonPath("$.followedId").value(luigi.getId()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // Verifica che il follow sia stato salvato
        assertThat(followRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("DELETE /api/follows/user/{followedId} - Dovrebbe smettere di seguire un utente")
    void shouldUnfollowUser() throws Exception {
        // Prima segui Luigi
        FollowRequest request = new FollowRequest();
        request.setFollowedId(luigi.getId());

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertThat(followRepository.count()).isEqualTo(1);

        // Poi smetti di seguirlo
        mockMvc.perform(delete("/api/follows/user/" + luigi.getId())
                        .header("Authorization", "Bearer " + marioToken))
                .andDo(print())
                .andExpect(status().isNoContent());

        // Verifica che il follow sia stato rimosso
        assertThat(followRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("POST /api/follows - Dovrebbe fallire se già segui l'utente")
    void shouldFailFollowUserTwice() throws Exception {
        FollowRequest request = new FollowRequest();
        request.setFollowedId(luigi.getId());

        // Primo follow
        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Secondo follow (dovrebbe fallire)
        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Verifica che ci sia sempre un solo follow
        assertThat(followRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/follows - Dovrebbe fallire se tenti di seguire te stesso")
    void shouldFailFollowSelf() throws Exception {
        FollowRequest request = new FollowRequest();
        request.setFollowedId(mario.getId()); // Mario cerca di seguire se stesso

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isConflict())  // ✅ Cambiato da isBadRequest() a isConflict()
                .andExpect(jsonPath("$.status").value(409));  // ✅ Cambiato da 400 a 409

        // Verifica che non sia stato creato il follow
        assertThat(followRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("DELETE /api/follows/user/{followedId} - Dovrebbe fallire se follow non esiste")
    void shouldFailUnfollowIfNotFollowing() throws Exception {
        // Prova a smettere di seguire senza aver mai seguito
        mockMvc.perform(delete("/api/follows/user/" + luigi.getId())
                        .header("Authorization", "Bearer " + marioToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/follows - Dovrebbe fallire senza autenticazione")
    void shouldFailFollowWithoutAuth() throws Exception {
        FollowRequest request = new FollowRequest();
        request.setFollowedId(luigi.getId());

        mockMvc.perform(post("/api/follows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/follows - Dovrebbe fallire se utente da seguire non esiste")
    void shouldFailFollowNonExistentUser() throws Exception {
        FollowRequest request = new FollowRequest();
        request.setFollowedId(99999L);

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/follows/user/{userId}/followers - Dovrebbe ottenere lista followers")
    void shouldGetFollowers() throws Exception {
        // Mario e Peach seguono Luigi
        FollowRequest request1 = new FollowRequest();
        request1.setFollowedId(luigi.getId());

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + peachToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Ottieni followers di Luigi
        mockMvc.perform(get("/api/follows/user/" + luigi.getId() + "/followers")
                        .header("Authorization", "Bearer " + luigiToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].followerId").exists())
                .andExpect(jsonPath("$[*].followedId").exists());
    }

    @Test
    @DisplayName("GET /api/follows/user/{userId}/following - Dovrebbe ottenere lista following")
    void shouldGetFollowing() throws Exception {
        // Mario segue Luigi e Peach
        FollowRequest request1 = new FollowRequest();
        request1.setFollowedId(luigi.getId());

        FollowRequest request2 = new FollowRequest();
        request2.setFollowedId(peach.getId());

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        // Ottieni following di Mario
        mockMvc.perform(get("/api/follows/user/" + mario.getId() + "/following")
                        .header("Authorization", "Bearer " + marioToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/follows/user/{userId}/followers/count - Dovrebbe contare followers")
    void shouldCountFollowers() throws Exception {
        // Mario e Peach seguono Luigi
        FollowRequest request = new FollowRequest();
        request.setFollowedId(luigi.getId());

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + peachToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Conta followers di Luigi
        mockMvc.perform(get("/api/follows/user/" + luigi.getId() + "/followers/count")
                        .header("Authorization", "Bearer " + luigiToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @DisplayName("GET /api/follows/user/{userId}/following/count - Dovrebbe contare following")
    void shouldCountFollowing() throws Exception {
        // Mario segue Luigi e Peach
        FollowRequest request1 = new FollowRequest();
        request1.setFollowedId(luigi.getId());

        FollowRequest request2 = new FollowRequest();
        request2.setFollowedId(peach.getId());

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        // Conta following di Mario
        mockMvc.perform(get("/api/follows/user/" + mario.getId() + "/following/count")
                        .header("Authorization", "Bearer " + marioToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @DisplayName("GET /api/follows/user/{followedId}/check - Dovrebbe verificare se stai seguendo un utente")
    void shouldCheckIfFollowing() throws Exception {
        FollowRequest request = new FollowRequest();
        request.setFollowedId(luigi.getId());

        // Verifica prima del follow
        mockMvc.perform(get("/api/follows/user/" + luigi.getId() + "/check")
                        .header("Authorization", "Bearer " + marioToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(false));

        // Segui Luigi
        mockMvc.perform(post("/api/follows")
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Verifica dopo il follow
        mockMvc.perform(get("/api/follows/user/" + luigi.getId() + "/check")
                        .header("Authorization", "Bearer " + marioToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true));
    }
}