package com.projectguard.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 앱 소셜 로그인 (AppLoginService 참고). 코드 발급은 웹 서버만, 교환은 앱이 호출한다. */
@RestController
@RequestMapping("/api/auth/app-login")
public class AppLoginController {

    private final AppLoginService appLoginService;
    private final AuthService authService;
    private final String internalSyncSecret;

    public AppLoginController(
            AppLoginService appLoginService,
            AuthService authService,
            @Value("${INTERNAL_SYNC_SECRET:}") String internalSyncSecret
    ) {
        this.appLoginService = appLoginService;
        this.authService = authService;
        this.internalSyncSecret = internalSyncSecret;
    }

    public record IssueRequest(String sessionToken, String codeChallenge) {
    }

    public record IssueResponse(String code) {
    }

    public record ExchangeRequest(String code, String codeVerifier) {
    }

    /** 웹(Next.js 서버)이 oauth-sync로 받은 세션 토큰을 맡기고 1회용 코드를 받는다. 내부 비밀값 필수. */
    @PostMapping("/codes")
    public IssueResponse issue(
            @RequestBody IssueRequest request,
            @RequestHeader(value = "X-Internal-Secret", required = false) String providedSecret
    ) {
        if (internalSyncSecret.isBlank() || providedSecret == null || !MessageDigest.isEqual(
                internalSyncSecret.getBytes(StandardCharsets.UTF_8), providedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidCredentialsException("내부 전용 엔드포인트입니다.");
        }
        User user = authService.validate(request.sessionToken())
                .orElseThrow(() -> new InvalidCredentialsException("세션이 유효하지 않습니다."));
        return new IssueResponse(appLoginService.issueCode(request.sessionToken(), user.getEmail(), request.codeChallenge()));
    }

    @PostMapping("/exchange")
    public AuthController.AuthResponse exchange(@RequestBody ExchangeRequest request) {
        AppLoginService.Exchanged result = appLoginService.exchange(request.code(), request.codeVerifier());
        return new AuthController.AuthResponse(result.token(), result.email());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleInvalidCredentials(InvalidCredentialsException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
