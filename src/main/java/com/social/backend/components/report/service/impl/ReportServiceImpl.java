package com.social.backend.components.report.service.impl;

import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.notification.enums.NotificationType;
import com.social.backend.components.notification.service.NotificationService;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.report.dto.CreateReportRequest;
import com.social.backend.components.report.dto.ReportResponse;
import com.social.backend.components.report.entity.Report;
import com.social.backend.components.report.repository.ReportRepository;
import com.social.backend.components.report.service.ReportService;
import com.social.backend.components.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ReportResponse create(Long reporterId, CreateReportRequest request) {
        if (reportRepository.existsByReporterIdAndPostId(reporterId, request.getPostId())) {
            throw new IllegalStateException("Hai già segnalato questo post");
        }
        var reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));
        var post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato"));

        var report = Report.builder()
                .reporter(reporter)
                .post(post)
                .reason(request.getReason())
                .notes(request.getNotes())
                .build();

        ReportResponse response = mapToResponse(reportRepository.save(report));

        // Notifica tutti gli admin
        userRepository.findByRole("ADMIN").forEach(admin -> {
            try {
                notificationService.createNotification(
                        admin.getId(),
                        reporterId,
                        NotificationType.REPORT,
                        post.getId(),
                        null,
                        "@" + reporter.getUsername() + " ha segnalato un post per: " + request.getReason().name().replace("_", " ").toLowerCase()
                );
            } catch (Exception ignored) {}
        });

        return response;
    }

    @Override
    public Page<ReportResponse> getAll(int page, int size) {
        return reportRepository.findAllWithDetails(PageRequest.of(page, size)).map(this::mapToResponse);
    }

    @Override
    public Page<ReportResponse> getByStatus(Report.ReportStatus status, int page, int size) {
        return reportRepository.findByStatusWithDetails(status, PageRequest.of(page, size)).map(this::mapToResponse);
    }

    @Override
    @Transactional
    public ReportResponse updateStatus(Long reportId, Report.ReportStatus status) {
        var report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Segnalazione non trovata"));
        report.setStatus(status);

        // Se accettata → nascondi il post dal feed
        if (status == Report.ReportStatus.REVIEWED) {
            postRepository.hidePost(report.getPost().getId());
        }

        return mapToResponse(reportRepository.save(report));
    }

    @Override
    public long countPending() {
        return reportRepository.countByStatus(Report.ReportStatus.PENDING);
    }

    private ReportResponse mapToResponse(Report r) {
        return ReportResponse.builder()
                .id(r.getId())
                .postId(r.getPost().getId())
                .postContent(r.getPost().getContent())
                .postAuthorUsername(r.getPost().getAuthor().getUsername())
                .reporterId(r.getReporter().getId())
                .reporterUsername(r.getReporter().getUsername())
                .reason(r.getReason())
                .status(r.getStatus())
                .notes(r.getNotes())
                .createdAt(r.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .build();
    }
}