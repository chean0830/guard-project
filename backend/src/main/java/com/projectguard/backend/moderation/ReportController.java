package com.projectguard.backend.moderation;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.InvalidCredentialsException;
import com.projectguard.backend.auth.User;
import com.projectguard.backend.consultation.ConsultationAccessDeniedException;
import com.projectguard.backend.consultation.SenderType;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/**
 * 상담 대화방에서 상대방을 신고하는 API. 회원은 회원 세션으로 변호사를, 변호사는 변호사 세션으로
 * 회원을 신고한다 — 경로는 각자의 상담 API 아래에 두어 기존 인증 구분(/api/consultations vs
 * /api/lawyer/consultations)을 그대로 따른다.
 */
@RestController
public class ReportController {

    private final ModerationService moderationService;
    private final AuthService authService;
    private final LawyerAuthService lawyerAuthService;

    public ReportController(
            ModerationService moderationService,
            AuthService authService,
            LawyerAuthService lawyerAuthService
    ) {
        this.moderationService = moderationService;
        this.authService = authService;
        this.lawyerAuthService = lawyerAuthService;
    }

    public record ReportRequest(ReportReason reason, String detail) {
    }

    public record ReportResponse(Long id, String message) {
    }

    @PostMapping("/api/consultations/{id}/report")
    public ReportResponse reportAsUser(
            @PathVariable Long id,
            @RequestBody ReportRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User user = authService.validate(extractToken(authorization))
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
        Report report = moderationService.submitReport(id, SenderType.USER, user.getId(), request.reason(), request.detail());
        return accepted(report);
    }

    @PostMapping("/api/lawyer/consultations/{id}/report")
    public ReportResponse reportAsLawyer(
            @PathVariable Long id,
            @RequestBody ReportRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Lawyer lawyer = lawyerAuthService.validate(extractToken(authorization))
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
        Report report = moderationService.submitReport(id, SenderType.LAWYER, lawyer.getId(), request.reason(), request.detail());
        return accepted(report);
    }

    private ReportResponse accepted(Report report) {
        return new ReportResponse(report.getId(), "신고가 접수되었습니다. 관리자가 대화 내용을 확인한 뒤 조치합니다.");
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleInvalidCredentials(InvalidCredentialsException e) {
        return e.getMessage();
    }

    @ExceptionHandler(ConsultationAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(ConsultationAccessDeniedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException e) {
        return e.getMessage();
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(RuntimeException e) {
        return e.getMessage();
    }
}
