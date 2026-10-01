package com.projectguard.backend.moderation;

import com.projectguard.backend.consultation.SenderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 상담 대화방 안에서 한쪽이 상대방을 신고한 기록. 회원/변호사 엔티티와는 ID로만 연결한다
 * (Consultation과 같은 패턴). 신고 대상은 항상 같은 대화방의 상대방이라, 신고자 쪽 타입만
 * 알면 대상 타입이 정해진다.
 */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long consultationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    private SenderType reporterType;

    @Column(nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    private SenderType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    private ReportReason reason;

    @Column(length = 1000)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    private ReportStatus status = ReportStatus.PENDING;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;

    protected Report() {
    }

    public Report(
            Long consultationId,
            SenderType reporterType,
            Long reporterId,
            SenderType targetType,
            Long targetId,
            ReportReason reason,
            String detail
    ) {
        this.consultationId = consultationId;
        this.reporterType = reporterType;
        this.reporterId = reporterId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
    }

    public void resolve(ReportStatus status) {
        this.status = status;
        this.resolvedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getConsultationId() {
        return consultationId;
    }

    public SenderType getReporterType() {
        return reporterType;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public SenderType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
