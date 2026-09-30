package com.projectguard.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 비밀번호 찾기(재설정 메일 요청)와 새 비밀번호 설정. 회원·변호사 공통이며 로그인 없이 호출한다. */
@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    public record ResetRequest(String email, String accountType) {
    }

    public record ConfirmRequest(String token, String newPassword) {
    }

    public record MessageResponse(String message) {
    }

    @PostMapping("/request")
    public MessageResponse request(@RequestBody ResetRequest request) {
        passwordResetService.requestReset(request.email(), request.accountType());
        return new MessageResponse("가입된 이메일이라면 비밀번호 재설정 링크를 보냈어요. 메일함(스팸함 포함)을 확인해주세요.");
    }

    @PostMapping("/confirm")
    public MessageResponse confirm(@RequestBody ConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return new MessageResponse("새 비밀번호가 설정됐어요. 이제 로그인할 수 있어요.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
