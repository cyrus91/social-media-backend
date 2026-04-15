package com.social.backend.components.report.repository;

import com.social.backend.components.report.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndPostId(Long reporterId, Long postId);

    // Verifica se esiste una segnalazione attiva (PENDING o REVIEWED) — non conta le DISMISSED
    // Questo permette di ri-segnalare dopo che una segnalazione è stata respinta
    @Query("SELECT COUNT(r) > 0 FROM Report r WHERE r.reporter.id = :reporterId AND r.post.id = :postId AND r.status <> com.social.backend.components.report.entity.Report.ReportStatus.DISMISSED")
    boolean existsActiveByReporterIdAndPostId(@Param("reporterId") Long reporterId, @Param("postId") Long postId);

    @Query("SELECT r FROM Report r JOIN FETCH r.reporter JOIN FETCH r.post JOIN FETCH r.post.author ORDER BY r.createdAt DESC")
    Page<Report> findAllWithDetails(Pageable pageable);

    @Query("SELECT r FROM Report r JOIN FETCH r.reporter JOIN FETCH r.post JOIN FETCH r.post.author WHERE r.status = :status ORDER BY r.createdAt DESC")
    Page<Report> findByStatusWithDetails(@Param("status") Report.ReportStatus status, Pageable pageable);

    long countByStatus(Report.ReportStatus status);
}