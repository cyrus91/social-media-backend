package com.social.backend.components.like.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.backend.components.auth.dto.LoginRequest;
import com.social.backend.components.auth.dto.LoginResponse;
import com.social.backend.components.like.dto.LikeRequest;
import com.social.backend.components.like.repository.LikeRepository;
import com.social.backend.components.post.entity.Post;
import com.social.backend.components.post.repository.PostRepository;
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
@DisplayName("LikeController Integration Tests")
class LikeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private User otherUser;
    private Post testPost;
    private String jwtToken;
    private String otherJwtToken;

    @BeforeEach
    void setUp() throws Exception {
        // Pulisci database
        likeRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        // Crea utenti
        testUser = User.builder()
                .username("mario_rossi")
                .email("mario@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .build();
        userRepository.save(testUser);

        otherUser = User.builder()
                .username("luigi_verdi")
                .email("luigi@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .build();
        userRepository.save(otherUser);

        // Crea post di test
        testPost = Post.builder()
                .content("Post per testare i like")
                .author(testUser)
                .build();
        postRepository.save(testPost);

        // Ottieni JWT token
        jwtToken = getJwtToken("mario_rossi", "password123");
        otherJwtToken = getJwtToken("luigi_verdi", "password123");
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
    @DisplayName("POST /api/likes - Dovrebbe aggiungere un like")
    void shouldLikePost() throws Exception {
        LikeRequest request = new LikeRequest();
        request.setPostId(testPost.getId());

        mockMvc.perform(post("/api/likes")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(testUser.getId()))
                .andExpect(jsonPath("$.username").value("mario_rossi"))
                .andExpect(jsonPath("$.postId").value(testPost.getId()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // Verifica che il like sia stato salvato
        assertThat(likeRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("DELETE /api/likes/post/{postId} - Dovrebbe rimuovere un like")
    void shouldUnlikePost() throws Exception {
        // Prima aggiungi un like
        LikeRequest request = new LikeRequest();
        request.setPostId(testPost.getId());

        mockMvc.perform(post("/api/likes")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertThat(likeRepository.count()).isEqualTo(1);

        // Poi rimuovilo
        mockMvc.perform(delete("/api/likes/post/" + testPost.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andDo(print())
                .andExpect(status().isNoContent());

        // Verifica che il like sia stato rimosso
        assertThat(likeRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("POST /api/likes - Dovrebbe fallire se già messo like")
    void shouldFailLikePostTwice() throws Exception {
        LikeRequest request = new LikeRequest();
        request.setPostId(testPost.getId());

        // Primo like
        mockMvc.perform(post("/api/likes")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Secondo like (dovrebbe fallire)
        mockMvc.perform(post("/api/likes")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Verifica che ci sia sempre un solo like
        assertThat(likeRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("DELETE /api/likes/post/{postId} - Dovrebbe fallire se like non esiste")
    void shouldFailUnlikePostIfNotLiked() throws Exception {
        // Prova a rimuovere like senza averlo mai messo
        mockMvc.perform(delete("/api/likes/post/" + testPost.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/likes - Dovrebbe fallire senza autenticazione")
    void shouldFailLikeWithoutAuth() throws Exception {
        LikeRequest request = new LikeRequest();
        request.setPostId(testPost.getId());

        mockMvc.perform(post("/api/likes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/likes - Dovrebbe fallire se post non esiste")
    void shouldFailLikeNonExistentPost() throws Exception {
        LikeRequest request = new LikeRequest();
        request.setPostId(99999L);

        mockMvc.perform(post("/api/likes")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/likes/post/{postId}/count - Dovrebbe contare i like correttamente")
    void shouldCountLikesCorrectly() throws Exception {
        LikeRequest request = new LikeRequest();
        request.setPostId(testPost.getId());

        // Mario mette like
        mockMvc.perform(post("/api/likes")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Luigi mette like
        mockMvc.perform(post("/api/likes")
                        .header("Authorization", "Bearer " + otherJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Verifica conteggio
        assertThat(likeRepository.count()).isEqualTo(2);

        // Verifica endpoint count
        mockMvc.perform(get("/api/likes/post/" + testPost.getId() + "/count")
                        .header("Authorization", "Bearer " + jwtToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @DisplayName("GET /api/likes/post/{postId}/check - Dovrebbe verificare se utente ha messo like")
    void shouldCheckIfUserLikedPost() throws Exception {
        LikeRequest request = new LikeRequest();
        request.setPostId(testPost.getId());

        // Verifica prima del like
        mockMvc.perform(get("/api/likes/post/" + testPost.getId() + "/check")
                        .header("Authorization", "Bearer " + jwtToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false));

        // Metti like
        mockMvc.perform(post("/api/likes")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Verifica dopo il like
        mockMvc.perform(get("/api/likes/post/" + testPost.getId() + "/check")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true));
    }

    @Test
    @DisplayName("GET /api/likes/post/{postId} - Dovrebbe ottenere lista utenti che hanno messo like")
    void shouldGetUsersWhoLiked() throws Exception {
        LikeRequest request = new LikeRequest();
        request.setPostId(testPost.getId());

        // Mario e Luigi mettono like
        mockMvc.perform(post("/api/likes")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/likes")
                        .header("Authorization", "Bearer " + otherJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Ottieni lista
        mockMvc.perform(get("/api/likes/post/" + testPost.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").isNumber())
                .andExpect(jsonPath("$[0].username").isString())
                .andExpect(jsonPath("$[0].postId").value(testPost.getId()));
    }
}