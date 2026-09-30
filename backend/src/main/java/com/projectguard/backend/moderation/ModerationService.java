package com.projectguard.backend.moderation;

import com.projectguard.backend.auth.User;
import com.projectguard.backend.auth.UserRepository;
import com.projectguard.backend.consultation.Consultation;
import com.projectguard.backend.consultation.ConsultationService;
import com.projectguard.backend.consultation.SenderType;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 상담 대화 신고 접수와 관리자의 신고 처리/계정 정지를 담당한다. 정지된 계정은 로그인과
 * 기존 세션 사용이 모두 막히고(AuthService/LawyerAuthService.validate), 정지된 변호사는
 * 새 문의 매칭에서도 빠진다(ConsultationService.startConsultation).
 */
@Service
public class ModerationService {

    private static final int MAX_DETAIL_LENGTH = 1000;

    private final ReportRepository reportRepository;
    private final ConsultationService consultationService;
    private final UserRepository userRepository;
    private final LawyerRepository lawyerRepository;

    public ModerationService(
            ReportRepository reportRepository,
            ConsultationService consultationService,
            UserRepository userRepository,
            LawyerRepository lawyerRepository
    ) {
        this.reportRepository = reportRepository;
        this.consultationService = consultationService;
        this.userRepository = userRepository;
        this.lawyerRepository = lawyerRepository;
    }

    /** 대화방 당사자만 상대방을 신고할 수 있다. 같은 대화방에서 처리 전인 신고가 있으면 중복 접수하지 않는다. */
    public Report submitReport(
            Long consultationId, SenderType reporterType, Long reporterId, ReportReason reason, String detail
    ) {
        if (reason == null) {
            throw new IllegalArgumentException("신고 사유(욕설·모욕 또는 금전 요구)를 선택해주세요.");
        }
        if (detail != null && detail.length() > MAX_DETAIL_LENGTH) {
            throw new IllegalArgumentException("상세 내용은 " + MAX_DETAIL_LENGTH + "자 이내로 입력해주세요.");
        }
        Consultation consultation = consultationService.requireConsultationFor(consultationId, reporterType, reporterId);
        if (reportRepository.existsByConsultationIdAndReporterTypeAndReporterIdAndStatus(
                consultationId, reporterType, reporterId, ReportStatus.PENDING)) {
            throw new IllegalStateException("이미 접수된 신고가 검토 중입니다.");
        }

        SenderType targetType = reporterType == SenderType.USER ? SenderType.LAWYER : SenderType.USER;
        Long targetId = targetType == SenderType.LAWYER ? consultation.getLawyerId() : consultation.getUserId();
        String trimmedDetail = detail == null || detail.isBlank() ? null : detail.trim();
        return reportRepository.save(new Report(
                consultationId, reporterType, reporterId, targetType, targetId, reason, trimmedDetail));
    }

    public List<Report> listReports(ReportStatus status) {
        return status != null
                ? reportRepository.findByStatusOrderByCreatedAtDesc(status)
                : reportRepository.findAllByOrderByCreatedAtDesc();
    }

    public Report requireReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("신고를 찾을 수 없습니다."));
    }

    /**
     * 신고 기준을 충족한다고 판단한 경우: 신고 대상 계정을 정지하고, 같은 대상에 대해 쌓여 있던
     * 처리 전 신고도 함께 처리 완료로 바꾼다(이미 정지된 계정에 대한 신고를 하나씩 다시 볼 필요가 없게).
     */
    @Transactional
    public void actionReport(Long reportId) {
        Report report = requireReport(reportId);
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 신고입니다.");
        }
        String reason = report.getReason().getLabel();
        if (report.getTargetType() == SenderType.USER) {
            blockUser(report.getTargetId(), reason);
        } else {
            blockLawyer(report.getTargetId(), reason);
        }
        List<Report> pending = reportRepository.findByTargetTypeAndTargetIdAndStatus(
                report.getTargetType(), report.getTargetId(), ReportStatus.PENDING);
        for (Report r : pending) {
            r.resolve(ReportStatus.ACTIONED);
        }
        reportRepository.saveAll(pending);
    }

    public void dismissReport(Long reportId) {
        Report report = requireReport(reportId);
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 신고입니다.");
        }
        report.resolve(ReportStatus.DISMISSED);
        reportRepository.save(report);
    }

    public void blockUser(Long userId, String reason) {
        User user = requireUser(userId);
        user.block(reason);
        userRepository.save(user);
    }

    public void unblockUser(Long userId) {
        User user = requireUser(userId);
        user.unblock();
        userRepository.save(user);
    }

    public void blockLawyer(Long lawyerId, String reason) {
        Lawyer lawyer = requireLawyer(lawyerId);
        lawyer.block(reason);
        lawyerRepository.save(lawyer);
    }

    public void unblockLawyer(Long lawyerId) {
        Lawyer lawyer = requireLawyer(lawyerId);
        lawyer.unblock();
        lawyerRepository.save(lawyer);
    }

    public long reportCountAgainst(SenderType targetType, Long targetId) {
        return reportRepository.countByTargetTypeAndTargetId(targetType, targetId);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
    }

    private Lawyer requireLawyer(Long lawyerId) {
        return lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new NoSuchElementException("변호사를 찾을 수 없습니다."));
    }
}
