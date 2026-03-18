package com.social.backend.components.comment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.backend.components.auth.dto.LoginRequest;
import com.social.backend.components.auth.dto.LoginResponse;
import com.social.backend.components.comment.dto.CreateCommentRequest;
import com.social.backend.components.comment.dto.UpdateCommentRequest;
import com.social.backend.components.comment.entity.Comment;
import com.social.backend.components.comment.repository.CommentRepository;
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
@DisplayName("CommentController Integration Tests")
class CommentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User mario;
    private User luigi;
    private Post testPost;
    private String marioToken;
    private String luigiToken;

    @BeforeEach
    void setUp() throws Exception {
        // Pulisci database
        commentRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        // Crea utenti
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

        // Crea post di test
        testPost = Post.builder()
                .content("Post per testare i commenti")
                .author(mario)
                .build();
        postRepository.save(testPost);

        // Ottieni JWT token
        marioToken = getJwtToken("mario", "password123");
        luigiToken = getJwtToken("luigi", "password123");
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
    @DisplayName("POST /api/comments - Dovrebbe creare un commento")
    void shouldCreateComment() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setPostId(testPost.getId());
        request.setContent("Ottimo post!");

        mockMvc.perform(post("/api/comments")
                        .header("Authorization", "Bearer " + luigiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.content").value("Ottimo post!"))
                .andExpect(jsonPath("$.postId").value(testPost.getId()))
                .andExpect(jsonPath("$.authorId").value(luigi.getId()))
                .andExpect(jsonPath("$.authorUsername").value("luigi"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // Verifica che il commento sia stato salvato
        assertThat(commentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/comments - Dovrebbe fallire senza autenticazione")
    void shouldFailCreateCommentWithoutAuth() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setPostId(testPost.getId());
        request.setContent("Commento non autorizzato");

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/comments - Dovrebbe fallire con content vuoto")
    void shouldFailCreateCommentWithEmptyContent() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setPostId(testPost.getId());
        request.setContent(""); // Content vuoto

        mockMvc.perform(post("/api/comments")
                        .header("Authorization", "Bearer " + luigiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/comments - Dovrebbe fallire se post non esiste")
    void shouldFailCreateCommentOnNonExistentPost() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setPostId(99999L);
        request.setContent("Commento su post inesistente");

        mockMvc.perform(post("/api/comments")
                        .header("Authorization", "Bearer " + luigiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/comments/{id} - Dovrebbe ottenere un commento per ID")
    void shouldGetCommentById() throws Exception {
        // Crea un commento
        Comment comment = Comment.builder()
                .content("Commento di test")
                .post(testPost)
                .author(luigi)
                .build();
        commentRepository.save(comment);

        mockMvc.perform(get("/api/comments/" + comment.getId())
                        .header("Authorization", "Bearer " + marioToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(comment.getId()))
                .andExpect(jsonPath("$.content").value("Commento di test"))
                .andExpect(jsonPath("$.authorUsername").value("luigi"));
    }

    @Test
    @DisplayName("GET /api/comments/{id} - Dovrebbe fallire se commento non esiste")
    void shouldFailGetCommentByIdWhenNotFound() throws Exception {
        mockMvc.perform(get("/api/comments/99999")
                        .header("Authorization", "Bearer " + marioToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/comments/post/{postId} - Dovrebbe ottenere commenti per post")
    void shouldGetCommentsByPost() throws Exception {
        // Crea alcuni commenti
        Comment comment1 = Comment.builder()
                .content("Primo commento")
                .post(testPost)
                .author(luigi)
                .build();
        commentRepository.save(comment1);

        Comment comment2 = Comment.builder()
                .content("Secondo commento")
                .post(testPost)
                .author(mario)
                .build();
        commentRepository.save(comment2);

        Comment comment3 = Comment.builder()
                .content("Terzo commento")
                .post(testPost)
                .author(luigi)
                .build();
        commentRepository.save(comment3);

        mockMvc.perform(get("/api/comments/post/" + testPost.getId())
                        .header("Authorization", "Bearer " + marioToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].postId", everyItem(is(testPost.getId().intValue()))));
    }

    @Test
    @DisplayName("GET /api/comments/post/{postId} - Dovrebbe ottenere lista vuota se post non ha commenti")
    void shouldGetEmptyListForPostWithNoComments() throws Exception {
        mockMvc.perform(get("/api/comments/post/" + testPost.getId())
                        .header("Authorization", "Bearer " + marioToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("PUT /api/comments/{id} - Dovrebbe aggiornare un commento")
    void shouldUpdateComment() throws Exception {
        // Crea un commento di Luigi
        Comment comment = Comment.builder()
                .content("Contenuto originale")
                .post(testPost)
                .author(luigi)
                .build();
        commentRepository.save(comment);

        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("Contenuto aggiornato");

        mockMvc.perform(put("/api/comments/" + comment.getId())
                        .header("Authorization", "Bearer " + luigiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Contenuto aggiornato"));

        // Verifica nel database
        Comment updatedComment = commentRepository.findById(comment.getId()).orElseThrow();
        assertThat(updatedComment.getContent()).isEqualTo("Contenuto aggiornato");
    }

    @Test
    @DisplayName("PUT /api/comments/{id} - Dovrebbe fallire se non sei l'autore")
    void shouldFailUpdateCommentIfNotAuthor() throws Exception {
        // Crea un commento di Luigi
        Comment comment = Comment.builder()
                .content("Commento di Luigi")
                .post(testPost)
                .author(luigi)
                .build();
        commentRepository.save(comment);

        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("Tentativo di modifica da Mario");

        // Mario prova ad aggiornare il commento di Luigi
        mockMvc.perform(put("/api/comments/" + comment.getId())
                        .header("Authorization", "Bearer " + marioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // Verifica che non sia stato modificato
        Comment unchangedComment = commentRepository.findById(comment.getId()).orElseThrow();
        assertThat(unchangedComment.getContent()).isEqualTo("Commento di Luigi");
    }

    @Test
    @DisplayName("DELETE /api/comments/{id} - Dovrebbe eliminare un commento")
    void shouldDeleteComment() throws Exception {
        // Crea un commento di Luigi
        Comment comment = Comment.builder()
                .content("Commento da eliminare")
                .post(testPost)
                .author(luigi)
                .build();
        commentRepository.save(comment);

        Long commentId = comment.getId();

        mockMvc.perform(delete("/api/comments/" + commentId)
                        .header("Authorization", "Bearer " + luigiToken))
                .andDo(print())
                .andExpect(status().isNoContent());

        // Verifica che sia stato eliminato
        assertThat(commentRepository.findById(commentId)).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/comments/{id} - Dovrebbe fallire se non sei l'autore")
    void shouldFailDeleteCommentIfNotAuthor() throws Exception {
        // Crea un commento di Luigi
        Comment comment = Comment.builder()
                .content("Commento di Luigi")
                .post(testPost)
                .author(luigi)
                .build();
        commentRepository.save(comment);

        // Mario prova ad eliminare il commento di Luigi
        mockMvc.perform(delete("/api/comments/" + comment.getId())
                        .header("Authorization", "Bearer " + marioToken))
                .andDo(print())
                .andExpect(status().isForbidden());

        // Verifica che non sia stato eliminato
        assertThat(commentRepository.findById(comment.getId())).isPresent();
    }

    @Test
    @DisplayName("Paginazione - Dovrebbe paginare correttamente i commenti")
    void shouldPaginateComments() throws Exception {
        // Crea 25 commenti
        for (int i = 1; i <= 25; i++) {
            Comment comment = Comment.builder()
                    .content("Commento numero " + i)
                    .post(testPost)
                    .author(luigi)
                    .build();
            commentRepository.save(comment);
        }

        // Prima pagina (20 elementi)
        mockMvc.perform(get("/api/comments/post/" + testPost.getId())
                        .header("Authorization", "Bearer " + marioToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        // Seconda pagina (5 elementi rimanenti)
        mockMvc.perform(get("/api/comments/post/" + testPost.getId())
                        .header("Authorization", "Bearer " + marioToken)
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }
}