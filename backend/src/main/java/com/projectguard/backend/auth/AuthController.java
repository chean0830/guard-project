package com.projectguard.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입/로그인/로그아웃/세션 확인. 변호사 상담 기능만 로그인을 요구하고, 등기부등본 분석
 * (/api/analyze)은 로그인 없이 그대로 쓸 수 있다 (기획서상 핵심 기능은 누구나 접근 가능해야 함).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final String internalSyncSecret;

    public AuthController(
            AuthService authService,
            EmailVerificationService emailVerificationService,
            @Value("${INTERNAL_SYNC_SECRET:}") String internalSyncSecret
    ) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.internalSyncSecret = internalSyncSecret;
    }

    public record AuthRequest(String email, String password) {
    }

    public record AuthResponse(String token, String email) {
    }

    public record OAuthSyncRequest(String provider, String providerId, String email, Boolean emailVerified) {
    }

    public record SignupRequest(String email, String password, String verificationToken) {
    }

    public record EmailCodeRequest(String email, String code) {
    }

    public record VerificationResponse(String verificationToken) {
    }

    /** 1단계: 가입할 이메일로 6자리 인증번호를 보낸다. */
    @PostMapping("/signup/code")
    public void requestSignupCode(@RequestBody EmailCodeRequest request) {
        emailVerificationService.requestCode(request.email());
    }

    /** 2단계: 인증번호를 확인하고 가입용 1회용 토큰을 준다. */
    @PostMapping("/signup/verify")
    public VerificationResponse verifySignupCode(@RequestBody EmailCodeRequest request) {
        return new VerificationResponse(emailVerificationService.confirmCode(request.email(), request.code()));
    }

    /**
     * 3단계: 이메일 인증을 마친 경우에만 가입된다. 단, 내부 비밀값(INTERNAL_SYNC_SECRET)을 가진 서버 간 호출
     * (e2e 테스트 준비 등)은 인증을 건너뛴다 — 이 비밀값은 이미 oauth-sync로 계정을 만들 수 있는 권한이라 새로 열리는 권한은 없다.
     */
    @PostMapping("/signup")
    public AuthResponse signup(
            @RequestBody SignupRequest request,
            @RequestHeader(value = "X-Internal-Secret", required = false) String providedSecret
    ) {
        boolean internal = !internalSyncSecret.isBlank() && internalSyncSecret.equals(providedSecret);
        if (!internal) {
            emailVerificationService.consumeToken(request.email(), request.verificationToken());
        }
        AuthResult result = authService.signup(request.email(), request.password());
        return new AuthResponse(result.token(), result.email());
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthRequest request) {
        AuthResult result = authService.login(request.email(), request.password());
        return new AuthResponse(result.token(), result.email());
    }

    /**
     * 구글/카카오/네이버 콜백을 실제로 처리하는 건 프론트엔드(Next.js 서버 쪽)다. 이 엔드포인트는
     * 그 결과(이미 검증된 provider/providerId/email)를 받아 우리 서비스 세션 토큰만 발급하는
     * 내부 전용 API라, 브라우저가 아닌 서버끼리만 호출해야 한다 — 그래서 공유 비밀키로 한 번 더 막는다
     * (이 값이 없으면 아무나 이메일만 주장해서 로그인할 수 있게 되므로 필수).
     */
    @PostMapping("/oauth-sync")
    public AuthResponse oauthSync(
            @RequestBody OAuthSyncRequest request,
            @RequestHeader(value = "X-Internal-Secret", required = false) String providedSecret
    ) {
        if (internalSyncSecret.isBlank() || !internalSyncSecret.equals(providedSecret)) {
            throw new InvalidCredentialsException("내부 전용 엔드포인트입니다.");
        }
        AuthResult result = authService.oauthLogin(
                request.provider(), request.providerId(), request.email(), Boolean.TRUE.equals(request.emailVerified()));
        return new AuthResponse(result.token(), result.email());
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(extractToken(authorization));
    }

    @GetMapping("/me")
    public AuthResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.validate(extractToken(authorization))
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
        return new AuthResponse(null, user.getEmail());
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleEmailExists(EmailAlreadyExistsException e) {
        return e.getMessage();
    }

    @ExceptionHandler(LoginLockedException.class)
    @ResponseStatus(HttpStatus.LOCKED)
    public String handleLocked(LoginLockedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(AccountBlockedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleBlocked(AccountBlockedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleInvalidCredentials(InvalidCredentialsException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public String handleTooMany(IllegalStateException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
