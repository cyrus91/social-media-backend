package com.social.backend.components.report.service;

import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.notification.enums.NotificationType;
import com.social.backend.components.notification.repository.NotificationRepository;
import com.social.backend.components.notification.service.NotificationService;
import com.social.backend.components.post.entity.Post;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.report.dto.CreateReportRequest;
import com.social.backend.components.report.dto.ReportResponse;
import com.social.backend.components.report.entity.Report;
import com.social.backend.components.report.repository.ReportRepository;
import com.social.backend.components.report.service.impl.ReportServiceImpl;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.enums.Role;
import com.social.backend.components.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService Unit Tests")
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private NotificationRepository notificationRepository;

    @InjectMocks
    private ReportServiceImpl reportService;

    private User reporter;
    private User admin;
    private Post post;
    private Report savedReport;

    @BeforeEach
    void setUp() {
        reporter = User.builder().id(1L).username("reporter_user").build();
        admin = User.builder().id(99L).username("admin_user").role(Role.ADMIN).build();
        post = Post.builder().id(10L).author(reporter).content("Post da segnalare").build();

        savedReport = Report.builder()
                .id(1L)
                .reporter(reporter)
                .post(post)
                .reason(Report.ReportReason.SPAM)
                .status(Report.ReportStatus.PENDING)
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("Dovrebbe creare segnalazione e notificare gli admin")
        void shouldCreateReportAndNotifyAdmins() {
            CreateReportRequest request = new CreateReportRequest();
            request.setPostId(10L);
            request.setReason(Report.ReportReason.SPAM);
            request.setNotes("");

            when(reportRepository.existsByReporterIdAndPostIdAndStatusNot(1L, 10L, Report.ReportStatus.DISMISSED))
                    .thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
            when(postRepository.findById(10L)).thenReturn(Optional.of(post));
            when(reportRepository.save(any())).thenReturn(savedReport);
            when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));

            ReportResponse result = reportService.create(1L, request);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);

            // Deve eliminare la vecchia notifica E crearne una nuova per ogni admin
            verify(notificationRepository).deleteByRecipientIdAndActorIdAndType(
                    99L, 1L, NotificationType.REPORT);
            verify(notificationService).createNotification(
                    eq(99L), eq(1L), eq(NotificationType.REPORT), eq(10L), isNull(), anyString());
        }

        @Test
        @DisplayName("Dovrebbe bloccare segnalazione duplicata PENDING")
        void shouldRejectDuplicatePendingReport() {
            CreateReportRequest request = new CreateReportRequest();
            request.setPostId(10L);
            request.setReason(Report.ReportReason.SPAM);

            when(reportRepository.existsByReporterIdAndPostIdAndStatusNot(1L, 10L, Report.ReportStatus.DISMISSED))
                    .thenReturn(true); // esiste già PENDING o REVIEWED

            assertThatThrownBy(() -> reportService.create(1L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("già segnalato");

            verify(reportRepository, never()).save(any());
            verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Dovrebbe permettere ri-segnalazione dopo DISMISSED")
        void shouldAllowReReportAfterDismissed() {
            CreateReportRequest request = new CreateReportRequest();
            request.setPostId(10L);
            request.setReason(Report.ReportReason.VIOLENCE);

            // DISMISSED non blocca — existsByStatusNot(DISMISSED) = false
            when(reportRepository.existsByReporterIdAndPostIdAndStatusNot(1L, 10L, Report.ReportStatus.DISMISSED))
                    .thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
            when(postRepository.findById(10L)).thenReturn(Optional.of(post));
            when(reportRepository.save(any())).thenReturn(savedReport);
            when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));

            ReportResponse result = reportService.create(1L, request);

            assertThat(result).isNotNull();
            verify(reportRepository).save(any(Report.class));
        }

        @Test
        @DisplayName("Dovrebbe lanciare eccezione se il post non esiste")
        void shouldThrowIfPostNotFound() {
            CreateReportRequest request = new CreateReportRequest();
            request.setPostId(999L);
            request.setReason(Report.ReportReason.SPAM);

            when(reportRepository.existsByReporterIdAndPostIdAndStatusNot(1L, 999L, Report.ReportStatus.DISMISSED))
                    .thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
            when(postRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.create(1L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("REVIEWED dovrebbe nascondere il post")
        void shouldHidePostWhenReviewed() {
            savedReport.setStatus(Report.ReportStatus.PENDING);
            when(reportRepository.findById(1L)).thenReturn(Optional.of(savedReport));
            when(reportRepository.save(any())).thenReturn(savedReport);

            reportService.updateStatus(1L, Report.ReportStatus.REVIEWED);

            verify(postRepository).hidePost(10L);
        }

        @Test
        @DisplayName("DISMISSED non dovrebbe nascondere il post")
        void shouldNotHidePostWhenDismissed() {
            savedReport.setStatus(Report.ReportStatus.PENDING);
            when(reportRepository.findById(1L)).thenReturn(Optional.of(savedReport));
            when(reportRepository.save(any())).thenReturn(savedReport);

            reportService.updateStatus(1L, Report.ReportStatus.DISMISSED);

            verify(postRepository, never()).hidePost(any());
        }

        @Test
        @DisplayName("Dovrebbe lanciare eccezione se la segnalazione non esiste")
        void shouldThrowIfReportNotFound() {
            when(reportRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.updateStatus(999L, Report.ReportStatus.REVIEWED))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}