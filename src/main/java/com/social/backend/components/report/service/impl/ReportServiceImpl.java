package com.social.backend.components.report.service.impl;

import com.social.backend.common.exception.ResourceNotFoundException;
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

    @Override
    @Transactional
    public ReportResponse create(Long reporterId, CreateReportRequest request) {
        // Un utente può segnalare un post una sola volta
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

        return mapToResponse(reportRepository.save(report));
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