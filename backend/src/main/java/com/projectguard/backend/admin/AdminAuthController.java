package com.projectguard.backend.admin;

import com.projectguard.backend.auth.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 로그인/로그아웃/세션 확인. 이 경로만 AdminAuthInterceptor의 관리자 인증 검사에서 제외된다. */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    public record LoginRequest(String email, String password) {
    }

    public record AdminAuthResponse(String token, String email) {
    }

    @PostMapping("/login")
    public AdminAuthResponse login(@RequestBody LoginRequest request) {
        String token = adminAuthService.login(request.email(), request.password());
        return new AdminAuthResponse(token, adminAuthService.getAdminEmail());
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        adminAuthService.logout(AdminAuthInterceptor.extractBearer(authorization));
    }

    @GetMapping("/me")
    public AdminAuthResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!adminAuthService.isValid(AdminAuthInterceptor.extractBearer(authorization))) {
            throw new InvalidCredentialsException("관리자 로그인이 필요합니다.");
        }
        return new AdminAuthResponse(null, adminAuthService.getAdminEmail());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleInvalidCredentials(InvalidCredentialsException e) {
        return e.getMessage();
    }
}
