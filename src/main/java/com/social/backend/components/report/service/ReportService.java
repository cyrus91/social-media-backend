package com.social.backend.components.report.service;

import com.social.backend.components.report.dto.CreateReportRequest;
import com.social.backend.components.report.dto.ReportResponse;
import com.social.backend.components.report.entity.Report;
import org.springframework.data.domain.Page;

public interface ReportService {
    ReportResponse create(Long reporterId, CreateReportRequest request);
    Page<ReportResponse> getAll(int page, int size);
    Page<ReportResponse> getByStatus(Report.ReportStatus status, int page, int size);
    ReportResponse updateStatus(Long reportId, Report.ReportStatus status);
    long countPending();
}