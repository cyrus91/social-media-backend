package com.social.backend.components.admin.service;

import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.admin.dto.AdminStatsResponse;
import com.social.backend.components.admin.service.impl.AdminServiceImpl;
import com.social.backend.components.comment.repository.CommentRepository;
import com.social.backend.components.follow.repository.FollowRepository;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.post.service.PostService;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.enums.Role;
import com.social.backend.components.user.repository.UserRepository;
import com.social.backend.components.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService Unit Tests")
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private FollowRepository followRepository;
    @Mock private UserService userService;
    @Mock private PostService postService;

    @InjectMocks
    private AdminServiceImpl adminService;

    private User normalUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        normalUser = User.builder().id(1L).username("cirolibero").role(Role.USER).banned(false).build();
        adminUser  = User.builder().id(2L).username("admin_user").role(Role.ADMIN).banned(false).build();
    }

    @Nested
    @DisplayName("getStats")
    class GetStats {

        @Test
        @DisplayName("Dovrebbe restituire statistiche aggregate")
        void shouldReturnAggregatedStats() {
            when(userRepository.count()).thenReturn(100L);
            when(postRepository.count()).thenReturn(500L);
            when(commentRepository.count()).thenReturn(1200L);
            when(userRepository.countByBanned(true)).thenReturn(3L);
            when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);

            AdminStatsResponse stats = adminService.getStats();

            assertThat(stats.getTotalUsers()).isEqualTo(100L);
            assertThat(stats.getTotalPosts()).isEqualTo(500L);
            assertThat(stats.getTotalComments()).isEqualTo(1200L);
            assertThat(stats.getBannedUsers()).isEqualTo(3L);
            assertThat(stats.getAdminUsers()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("toggleBan")
    class ToggleBan {

        @Test
        @DisplayName("Dovrebbe bannare un utente attivo")
        void shouldBanActiveUser() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(normalUser));
            when(userRepository.save(any())).thenReturn(normalUser);

            Map<String, Object> result = adminService.toggleBan(1L);

            assertThat(result.get("banned")).isEqualTo(true);
            assertThat(result.get("username")).isEqualTo("cirolibero");
            verify(userRepository).save(normalUser);
        }

        @Test
        @DisplayName("Dovrebbe sbannare un utente già bannato")
        void shouldUnbanBannedUser() {
            normalUser.setBanned(true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(normalUser));
            when(userRepository.save(any())).thenReturn(normalUser);

            Map<String, Object> result = adminService.toggleBan(1L);

            assertThat(result.get("banned")).isEqualTo(false);
        }

        @Test
        @DisplayName("Non dovrebbe permettere il ban di un admin")
        void shouldPreventBanningAdmin() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));

            assertThatThrownBy(() -> adminService.toggleBan(2L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("admin");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Dovrebbe lanciare eccezione se utente non esiste")
        void shouldThrowIfUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.toggleBan(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("changeRole")
    class ChangeRole {

        @Test
        @DisplayName("Dovrebbe promuovere utente a ADMIN")
        void shouldPromoteToAdmin() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(normalUser));
            when(userRepository.save(any())).thenReturn(normalUser);

            Map<String, Object> result = adminService.changeRole(1L, Role.ADMIN);

            assertThat(result.get("username")).isEqualTo("cirolibero");
            verify(userRepository).save(normalUser);
        }

        @Test
        @DisplayName("Dovrebbe degradare admin a USER")
        void shouldDemoteAdminToUser() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenReturn(adminUser);

            adminService.changeRole(2L, Role.USER);

            verify(userRepository).save(adminUser);
        }

        @Test
        @DisplayName("Dovrebbe lanciare eccezione se utente non esiste")
        void shouldThrowIfUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.changeRole(999L, Role.ADMIN))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteComment")
    class DeleteComment {

        @Test
        @DisplayName("Dovrebbe eliminare commento esistente")
        void shouldDeleteExistingComment() {
            when(commentRepository.existsById(5L)).thenReturn(true);

            adminService.deleteComment(5L);

            verify(commentRepository).deleteById(5L);
        }

        @Test
        @DisplayName("Dovrebbe lanciare eccezione se commento non esiste")
        void shouldThrowIfCommentNotFound() {
            when(commentRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> adminService.deleteComment(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}