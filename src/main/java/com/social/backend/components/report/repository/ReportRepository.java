package com.social.backend.components.report.repository;

import com.social.backend.components.report.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndPostId(Long reporterId, Long postId);

    @Query("SELECT r FROM Report r JOIN FETCH r.reporter JOIN FETCH r.post JOIN FETCH r.post.author ORDER BY r.createdAt DESC")
    Page<Report> findAllWithDetails(Pageable pageable);

    @Query("SELECT r FROM Report r JOIN FETCH r.reporter JOIN FETCH r.post JOIN FETCH r.post.author WHERE r.status = :status ORDER BY r.createdAt DESC")
    Page<Report> findByStatusWithDetails(@Param("status") Report.ReportStatus status, Pageable pageable);

    long countByStatus(Report.ReportStatus status);
}