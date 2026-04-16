package com.social.backend.components.post.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.backend.components.auth.dto.LoginRequest;
import com.social.backend.components.auth.dto.LoginResponse;
import com.social.backend.components.post.dto.CreatePostRequest;
import com.social.backend.components.post.dto.UpdatePostRequest;
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
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("PostController Integration Tests")
class PostControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private User otherUser;
    private String jwtToken;
    private String otherJwtToken;

    @BeforeEach
    void setUp() throws Exception {
        // Pulisci database
        postRepository.deleteAll();
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

        // Crea altro utente
        otherUser = User.builder()
                .username("luigi_verdi")
                .email("luigi@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .bio("Designer")
                .emailVerified(true)
                .build();
        userRepository.save(otherUser);

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
    @DisplayName("POST /api/posts - Dovrebbe creare un nuovo post")
    void shouldCreatePost() throws Exception {
        CreatePostRequest request = new CreatePostRequest();
        request.setContent("Questo è il mio primo post!");

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.content").value("Questo è il mio primo post!"))
                .andExpect(jsonPath("$.authorId").value(testUser.getId()))
                .andExpect(jsonPath("$.authorUsername").value("mario_rossi"))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.liked").value(false));

        // Verifica che il post sia stato salvato
        assertThat(postRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/posts - Dovrebbe creare post senza imageUrl")
    void shouldCreatePostWithoutImage() throws Exception {
        CreatePostRequest request = new CreatePostRequest();
        request.setContent("Post senza immagine");

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Post senza immagine"))
                .andExpect(jsonPath("$.imageUrl").isEmpty());
    }

    @Test
    @DisplayName("POST /api/posts - Dovrebbe fallire senza autenticazione")
    void shouldFailCreatePostWithoutAuth() throws Exception {
        CreatePostRequest request = new CreatePostRequest();
        request.setContent("Post non autorizzato");

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/posts - Dovrebbe fallire con contenuto vuoto")
    void shouldFailCreatePostWithEmptyContent() throws Exception {
        CreatePostRequest request = new CreatePostRequest();
        request.setContent("");  // Vuoto

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + jwtToken)  // ← CORRETTO
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/posts/{id} - Dovrebbe ottenere un post per ID")
    void shouldGetPostById() throws Exception {
        // Crea un post
        Post post = Post.builder()
                .content("Post di test")
                .imageUrl("https://example.com/test.jpg")
                .author(testUser)
                .build();
        postRepository.save(post);

        mockMvc.perform(get("/api/posts/" + post.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(post.getId()))
                .andExpect(jsonPath("$.content").value("Post di test"))
                .andExpect(jsonPath("$.authorId").value(testUser.getId()))
                .andExpect(jsonPath("$.authorUsername").value("mario_rossi"));
    }

    @Test
    @DisplayName("GET /api/posts/{id} - Dovrebbe fallire se post non esiste")
    void shouldFailGetPostByIdWhenNotFound() throws Exception {
        mockMvc.perform(get("/api/posts/99999")
                        .header("Authorization", "Bearer " + jwtToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Post non trovato con ID: 99999"));
    }

    @Test
    @DisplayName("GET /api/posts - Dovrebbe ottenere tutti i post (paginati)")
    void shouldGetAllPosts() throws Exception {
        // Crea alcuni post
        for (int i = 1; i <= 5; i++) {
            Post post = Post.builder()
                    .content("Post numero " + i)
                    .author(testUser)
                    .build();
            postRepository.save(post);
        }

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].authorUsername").value("mario_rossi"));
    }

    @Test
    @DisplayName("GET /api/posts/feed - Dovrebbe ottenere il feed personalizzato")
    void shouldGetPersonalizedFeed() throws Exception {
        // Crea post di test user
        Post post1 = Post.builder()
                .content("Mio post")
                .author(testUser)
                .build();
        postRepository.save(post1);

        // Crea post di altro utente
        Post post2 = Post.builder()
                .content("Post di Luigi")
                .author(otherUser)
                .build();
        postRepository.save(post2);

        mockMvc.perform(get("/api/posts/feed")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/posts/author/{authorId} - Dovrebbe ottenere post per authorId")
    void shouldGetPostsByAuthorId() throws Exception {
        // Crea post per test user
        Post post1 = Post.builder()
                .content("Post 1 di Mario")
                .author(testUser)
                .build();
        postRepository.save(post1);

        Post post2 = Post.builder()
                .content("Post 2 di Mario")
                .author(testUser)
                .build();
        postRepository.save(post2);

        // Crea post di altro utente
        Post post3 = Post.builder()
                .content("Post di Luigi")
                .author(otherUser)
                .build();
        postRepository.save(post3);

        mockMvc.perform(get("/api/posts/author/" + testUser.getId())
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].authorUsername", everyItem(is("mario_rossi"))));
    }

    @Test
    @DisplayName("PUT /api/posts/{id} - Dovrebbe aggiornare un post")
    void shouldUpdatePost() throws Exception {
        // Crea un post
        Post post = Post.builder()
                .content("Contenuto originale")
                .author(testUser)
                .build();
        postRepository.save(post);

        UpdatePostRequest request = new UpdatePostRequest();
        request.setContent("Contenuto aggiornato");

        mockMvc.perform(put("/api/posts/" + post.getId())
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Contenuto aggiornato"));

        // Verifica nel database
        Post updatedPost = postRepository.findById(post.getId()).orElseThrow();
        assertThat(updatedPost.getContent()).isEqualTo("Contenuto aggiornato");
    }

    @Test
    @DisplayName("PUT /api/posts/{id} - Dovrebbe fallire se non sei l'autore")
    void shouldFailUpdatePostIfNotAuthor() throws Exception {
        // Crea un post di test user
        Post post = Post.builder()
                .content("Post di Mario")
                .author(testUser)
                .build();
        postRepository.save(post);

        UpdatePostRequest request = new UpdatePostRequest();
        request.setContent("Tentativo di modifica");

        // Prova ad aggiornare con token di altro utente
        mockMvc.perform(put("/api/posts/" + post.getId())
                        .header("Authorization", "Bearer " + otherJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("DELETE /api/posts/{id} - Dovrebbe eliminare un post")
    void shouldDeletePost() throws Exception {
        // Crea un post
        Post post = Post.builder()
                .content("Post da eliminare")
                .author(testUser)
                .build();
        postRepository.save(post);

        Long postId = post.getId();

        mockMvc.perform(delete("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andDo(print())
                .andExpect(status().isNoContent());

        // Verifica che sia stato eliminato
        assertThat(postRepository.findById(postId)).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/posts/{id} - Dovrebbe fallire se non sei l'autore")
    void shouldFailDeletePostIfNotAuthor() throws Exception {
        // Crea un post di test user
        Post post = Post.builder()
                .content("Post di Mario")
                .author(testUser)
                .build();
        postRepository.save(post);

        // Prova ad eliminare con token di altro utente
        mockMvc.perform(delete("/api/posts/" + post.getId())
                        .header("Authorization", "Bearer " + otherJwtToken))
                .andDo(print())
                .andExpect(status().isForbidden());

        // Verifica che non sia stato eliminato
        assertThat(postRepository.findById(post.getId())).isPresent();
    }
}