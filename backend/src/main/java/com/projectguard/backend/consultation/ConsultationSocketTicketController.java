package com.projectguard.backend.consultation;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.InvalidCredentialsException;
import com.projectguard.backend.lawyer.LawyerAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/** 회원/변호사 각자의 세션으로 대화방 WebSocket 입장권을 받는다 (경로 구분은 신고 API와 같은 방식). */
@RestController
public class ConsultationSocketTicketController {

    private final ConsultationSocketTicketService ticketService;
    private final AuthService authService;
    private final LawyerAuthService lawyerAuthService;

    public ConsultationSocketTicketController(
            ConsultationSocketTicketService ticketService,
            AuthService authService,
            LawyerAuthService lawyerAuthService
    ) {
        this.ticketService = ticketService;
        this.authService = authService;
        this.lawyerAuthService = lawyerAuthService;
    }

    public record TicketResponse(String ticket) {
    }

    @PostMapping("/api/consultations/{id}/socket-ticket")
    public TicketResponse issueForUser(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = authService.validate(extractToken(authorization))
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."))
                .getId();
        return new TicketResponse(ticketService.issue(id, SenderType.USER, userId));
    }

    @PostMapping("/api/lawyer/consultations/{id}/socket-ticket")
    public TicketResponse issueForLawyer(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long lawyerId = lawyerAuthService.validate(extractToken(authorization))
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."))
                .getId();
        return new TicketResponse(ticketService.issue(id, SenderType.LAWYER, lawyerId));
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
}
