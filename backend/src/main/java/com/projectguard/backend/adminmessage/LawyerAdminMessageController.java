package com.projectguard.backend.adminmessage;

import com.projectguard.backend.auth.InvalidCredentialsException;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 변호사의 관리자 메시지 알림함 (읽기 전용). */
@RestController
@RequestMapping("/api/lawyer/admin-messages")
public class LawyerAdminMessageController {

    private final AdminMessageService service;
    private final LawyerAuthService lawyerAuthService;

    public LawyerAdminMessageController(AdminMessageService service, LawyerAuthService lawyerAuthService) {
        this.service = service;
        this.lawyerAuthService = lawyerAuthService;
    }

    public record UnreadCountResponse(long unreadCount) {
    }

    @GetMapping
    public List<AdminMessageController.MessageDto> list(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return service.listFor(requireLawyer(authorization).getId()).stream()
                .map(AdminMessageController.MessageDto::of)
                .toList();
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return new UnreadCountResponse(service.unreadCount(requireLawyer(authorization).getId()));
    }

    @PostMapping("/read")
    public void markAllRead(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        service.markAllRead(requireLawyer(authorization).getId());
    }

    private Lawyer requireLawyer(String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : null;
        return lawyerAuthService.validate(token).orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleInvalidCredentials(InvalidCredentialsException e) {
        return e.getMessage();
    }
}
