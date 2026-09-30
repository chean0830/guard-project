package com.projectguard.backend.moderation;

import com.projectguard.backend.consultation.SenderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findAllByOrderByCreatedAtDesc();

    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<Report> findByTargetTypeAndTargetIdAndStatus(SenderType targetType, Long targetId, ReportStatus status);

    long countByTargetTypeAndTargetId(SenderType targetType, Long targetId);

    boolean existsByConsultationIdAndReporterTypeAndReporterIdAndStatus(
            Long consultationId, SenderType reporterType, Long reporterId, ReportStatus status);
}
