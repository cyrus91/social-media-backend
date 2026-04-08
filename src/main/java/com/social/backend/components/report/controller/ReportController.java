package com.social.backend.components.report.controller;

import com.social.backend.components.report.dto.CreateReportRequest;
import com.social.backend.components.report.dto.ReportResponse;
import com.social.backend.components.report.entity.Report;
import com.social.backend.components.report.service.ReportService;
import com.social.backend.security.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // POST /api/reports — utente segnala un post
    @PostMapping
    public ResponseEntity<ReportResponse> create(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody CreateReportRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(reportService.create(userDetails.getUser().getId(), request));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    // GET /api/reports — admin: lista tutte le segnalazioni
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<ReportResponse> getAll(
            @RequestParam(required = false) Report.ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (status != null) return reportService.getByStatus(status, page, size);
        return reportService.getAll(page, size);
    }

    // PATCH /api/reports/{id}/status — admin: aggiorna stato
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ReportResponse updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        var status = Report.ReportStatus.valueOf(body.get("status"));
        return reportService.updateStatus(id, status);
    }

    // GET /api/reports/pending/count — admin: contatore badge
    @GetMapping("/pending/count")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> pendingCount() {
        return Map.of("count", reportService.countPending());
    }
}