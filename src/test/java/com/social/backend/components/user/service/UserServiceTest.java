package com.social.backend.components.user.service;

import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.user.dto.UpdateUserRequest;
import com.social.backend.components.user.dto.UserResponse;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Usa il builder di Lombok
        testUser = User.builder()
                .id(1L)
                .username("mario_rossi")
                .email("mario@example.com")
                .passwordHash("hashedPassword123")  // ✅ passwordHash invece di password
                .bio("Sviluppatore Java")
                .avatarUrl("https://example.com/avatar.jpg")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Dovrebbe ottenere il profilo utente per ID")
    void shouldGetUserProfile() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        UserResponse result = userService.getById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("mario_rossi");
        assertThat(result.getEmail()).isEqualTo("mario@example.com");
        assertThat(result.getBio()).isEqualTo("Sviluppatore Java");

        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Dovrebbe lanciare ResourceNotFoundException se utente non trovato")
    void shouldThrowExceptionWhenUserNotFound() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Utente non trovato");

        verify(userRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Dovrebbe ottenere il profilo utente per username")
    void shouldGetUserProfileByUsername() {
        // Given
        when(userRepository.findByUsername("mario_rossi")).thenReturn(Optional.of(testUser));

        // When
        UserResponse result = userService.getByUsername("mario_rossi");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("mario_rossi");

        verify(userRepository, times(1)).findByUsername("mario_rossi");
    }

    @Test
    @DisplayName("Dovrebbe aggiornare il profilo utente (senza password)")
    void shouldUpdateUserProfile() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("mario.new@example.com");
        request.setBio("Nuovo bio aggiornato");
        request.setAvatarUrl("https://example.com/new-avatar.jpg");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("mario.new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        UserResponse result = userService.updateCurrentUser(1L, request);

        // Then
        assertThat(result).isNotNull();
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).existsByEmail("mario.new@example.com");
        verify(userRepository, times(1)).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString()); // Password NON deve essere chiamato
    }

    @Test
    @DisplayName("Dovrebbe aggiornare il profilo utente (con password)")
    void shouldUpdateUserProfileWithPassword() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("mario@example.com");
        request.setNewPassword("newPassword123");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newPassword123")).thenReturn("hashedNewPassword123");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        UserResponse result = userService.updateCurrentUser(1L, request);

        // Then
        assertThat(result).isNotNull();
        verify(passwordEncoder, times(1)).encode("newPassword123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Dovrebbe ottenere l'utente corrente")
    void shouldGetCurrentUser() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        UserResponse result = userService.getCurrentUser(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("mario_rossi");
        assertThat(result.getEmail()).isEqualTo("mario@example.com");

        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Dovrebbe creare un User con il builder di Lombok")
    void shouldCreateUserWithBuilder() {
        // When
        User newUser = User.builder()
                .username("test_user")
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .bio("Test bio")
                .avatarUrl("https://example.com/avatar.jpg")
                .build();

        // Then
        assertThat(newUser).isNotNull();
        assertThat(newUser.getUsername()).isEqualTo("test_user");
        assertThat(newUser.getEmail()).isEqualTo("test@example.com");
        assertThat(newUser.getPasswordHash()).isEqualTo("hashedPassword");
        assertThat(newUser.getBio()).isEqualTo("Test bio");
        assertThat(newUser.getAvatarUrl()).isEqualTo("https://example.com/avatar.jpg");
    }

    @Test
    @DisplayName("Dovrebbe impostare createdAt automaticamente con @PrePersist")
    void shouldSetCreatedAtAutomatically() {
        // Given
        User newUser = User.builder()
                .username("test_user")
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .build();

        // When
        // Simula @PrePersist usando reflection per accedere al metodo protected
        try {
            java.lang.reflect.Method onCreateMethod = User.class.getDeclaredMethod("onCreate");
            onCreateMethod.setAccessible(true);
            onCreateMethod.invoke(newUser);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Then
        assertThat(newUser.getCreatedAt()).isNotNull();
        assertThat(newUser.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }
}

