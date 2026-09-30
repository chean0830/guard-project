package com.projectguard.backend.moderation;

import com.projectguard.backend.auth.User;
import com.projectguard.backend.auth.UserRepository;
import com.projectguard.backend.consultation.ConsultationMessageRepository;
import com.projectguard.backend.consultation.SenderType;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 관리자 전용 신고 처리 및 회원/변호사 이용 정지 API. 관리자 인증은 /api/admin/** 전체에
 * 걸린 admin.AdminAuthInterceptor가 처리한다.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminModerationController {

    private final ModerationService moderationService;
    private final UserRepository userRepository;
    private final LawyerRepository lawyerRepository;
    private final ConsultationMessageRepository messageRepository;

    public AdminModerationController(
            ModerationService moderationService,
            UserRepository userRepository,
            LawyerRepository lawyerRepository,
            ConsultationMessageRepository messageRepository
    ) {
        this.moderationService = moderationService;
        this.userRepository = userRepository;
        this.lawyerRepository = lawyerRepository;
        this.messageRepository = messageRepository;
    }

    public record ReportSummary(
            Long id,
            Long consultationId,
            String reason,
            String reasonLabel,
            String detail,
            String status,
            Instant createdAt,
            Instant resolvedAt,
            String reporterType,
            String reporterName,
            String targetType,
            Long targetId,
            String targetName,
            String targetEmail,
            boolean targetBlocked,
            long targetReportCount
    ) {
    }

    public record MessageDto(String senderType, String content, Instant createdAt) {
    }

    public record UserSummary(
            Long id, String email, String name, String provider,
            boolean blocked, String blockedReason, Instant blockedAt, long reportCount
    ) {
    }

    public record LawyerMemberSummary(
            Long id, String email, String name, String lawFirm, String status,
            boolean blocked, String blockedReason, Instant blockedAt, long reportCount
    ) {
    }

    public record BlockRequest(String reason) {
    }

    @GetMapping("/reports")
    public List<ReportSummary> listReports(
            @RequestParam(required = false) ReportStatus status
    ) {
        return moderationService.listReports(status).stream().map(this::toSummary).toList();
    }

    /** 관리자가 신고 기준(욕설/금전 요구) 충족 여부를 판단할 수 있도록 해당 대화방의 원문 전체를 보여준다. */
    @GetMapping("/reports/{id}/messages")
    public List<MessageDto> reportMessages(
            @PathVariable Long id
    ) {
        Report report = moderationService.requireReport(id);
        return messageRepository.findByConsultationIdOrderByCreatedAtAsc(report.getConsultationId()).stream()
                .map(m -> new MessageDto(m.getSenderType().name(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    @PostMapping("/reports/{id}/action")
    public void actionReport(
            @PathVariable Long id
    ) {
        moderationService.actionReport(id);
    }

    @PostMapping("/reports/{id}/dismiss")
    public void dismissReport(
            @PathVariable Long id
    ) {
        moderationService.dismissReport(id);
    }

    /** 목록 한 페이지. 회원·변호사가 많아져도 관리자 화면이 느려지지 않도록 20명씩 나눠 보낸다. */
    public record PageResponse<T>(List<T> items, int page, int totalPages, long totalElements) {
    }

    private static final int PAGE_SIZE = 20;

    private static Pageable pageOf(int page) {
        return PageRequest.of(Math.max(0, page), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id"));
    }

    @GetMapping("/members/users")
    public PageResponse<UserSummary> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String q
    ) {
        Page<User> users = q == null || q.isBlank()
                ? userRepository.findAll(pageOf(page))
                : userRepository.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(q.trim(), q.trim(), pageOf(page));
        return new PageResponse<>(users.map(u -> new UserSummary(
                u.getId(), u.getEmail(), u.getName(), u.getProvider(),
                u.isBlocked(), u.getBlockedReason(), u.getBlockedAt(),
                moderationService.reportCountAgainst(SenderType.USER, u.getId()))).getContent(),
                users.getNumber(), users.getTotalPages(), users.getTotalElements());
    }

    @GetMapping("/members/lawyers")
    public PageResponse<LawyerMemberSummary> listLawyers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String q
    ) {
        Page<Lawyer> lawyers = q == null || q.isBlank()
                ? lawyerRepository.findAll(pageOf(page))
                : lawyerRepository.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(q.trim(), q.trim(), pageOf(page));
        return new PageResponse<>(lawyers.map(l -> new LawyerMemberSummary(
                l.getId(), l.getEmail(), l.getName(), l.getLawFirm(), l.getStatus().name(),
                l.isBlocked(), l.getBlockedReason(), l.getBlockedAt(),
                moderationService.reportCountAgainst(SenderType.LAWYER, l.getId()))).getContent(),
                lawyers.getNumber(), lawyers.getTotalPages(), lawyers.getTotalElements());
    }

    @PostMapping("/members/users/{id}/block")
    public void blockUser(
            @PathVariable Long id,
            @RequestBody(required = false) BlockRequest request
    ) {
        moderationService.blockUser(id, request != null ? request.reason() : null);
    }

    @PostMapping("/members/users/{id}/unblock")
    public void unblockUser(
            @PathVariable Long id
    ) {
        moderationService.unblockUser(id);
    }

    @PostMapping("/members/lawyers/{id}/block")
    public void blockLawyer(
            @PathVariable Long id,
            @RequestBody(required = false) BlockRequest request
    ) {
        moderationService.blockLawyer(id, request != null ? request.reason() : null);
    }

    @PostMapping("/members/lawyers/{id}/unblock")
    public void unblockLawyer(
            @PathVariable Long id
    ) {
        moderationService.unblockLawyer(id);
    }

    private ReportSummary toSummary(Report report) {
        Party reporter = party(report.getReporterType(), report.getReporterId());
        Party target = party(report.getTargetType(), report.getTargetId());
        return new ReportSummary(
                report.getId(),
                report.getConsultationId(),
                report.getReason().name(),
                report.getReason().getLabel(),
                report.getDetail(),
                report.getStatus().name(),
                report.getCreatedAt(),
                report.getResolvedAt(),
                report.getReporterType().name(),
                reporter.name(),
                report.getTargetType().name(),
                report.getTargetId(),
                target.name(),
                target.email(),
                target.blocked(),
                moderationService.reportCountAgainst(report.getTargetType(), report.getTargetId())
        );
    }

    private record Party(String name, String email, boolean blocked) {
    }

    private Party party(SenderType type, Long id) {
        if (type == SenderType.USER) {
            return userRepository.findById(id)
                    .map(u -> new Party(u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail(),
                            u.getEmail(), u.isBlocked()))
                    .orElse(new Party("알 수 없음", null, false));
        }
        return lawyerRepository.findById(id)
                .map(l -> new Party(l.getName(), l.getEmail(), l.isBlocked()))
                .orElse(new Party("알 수 없음", null, false));
    }



    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleConflict(IllegalStateException e) {
        return e.getMessage();
    }
}
