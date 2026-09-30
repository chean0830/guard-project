package com.projectguard.backend.auth;

import com.projectguard.backend.common.AccountMailService;
import com.projectguard.backend.common.RateLimitService;
import com.projectguard.backend.lawyer.LawyerAuthService;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 비밀번호 찾기. 가입한 이메일로 30분짜리 1회용 재설정 링크를 보내고, 링크로 새 비밀번호를 정하면
 * 로그인 잠금(5회 실패)도 함께 풀린다. 가입 여부를 알아낼 수 없도록, 없는 이메일로 요청해도 같은 응답을 준다.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();
    public static final int MAX_MAILS_PER_DAY = 5;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final LawyerRepository lawyerRepository;
    private final AuthService authService;
    private final LawyerAuthService lawyerAuthService;
    private final AccountMailService mailService;
    private final String frontendOrigin;
    private final RateLimitService rateLimitService;
    private final boolean logResetLinks;

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            LawyerRepository lawyerRepository,
            AuthService authService,
            LawyerAuthService lawyerAuthService,
            AccountMailService mailService,
            @Value("${FRONTEND_ORIGIN:http://localhost:3000}") String frontendOrigin,
            RateLimitService rateLimitService,
            @Value("${LOG_RESET_LINKS:false}") boolean logResetLinks
    ) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.lawyerRepository = lawyerRepository;
        this.authService = authService;
        this.lawyerAuthService = lawyerAuthService;
        this.mailService = mailService;
        this.frontendOrigin = frontendOrigin;
        this.rateLimitService = rateLimitService;
        this.logResetLinks = logResetLinks;
    }

    /** accountType: "USER" 또는 "LAWYER". 해당 계정이 있으면 재설정 링크를 메일로 보낸다. 없어도 조용히 끝난다. */
    public void requestReset(String email, String accountType) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일을 입력해주세요.");
        }
        String type = "LAWYER".equals(accountType) ? "LAWYER" : "USER";
        // 가입 여부와 상관없이 이메일마다 센다 — 그래야 제한에 걸리는지로 가입 여부를 알아낼 수 없다.
        if (!rateLimitService.tryConsume("reset-mail-" + type, email, MAX_MAILS_PER_DAY)) {
            throw new IllegalStateException("비밀번호 찾기 메일은 하루 " + MAX_MAILS_PER_DAY + "번까지 보낼 수 있어요. 내일 다시 시도해주세요.");
        }
        Optional<Long> accountId = "LAWYER".equals(type)
                ? lawyerRepository.findByEmail(email.trim()).map(l -> l.getId())
                : userRepository.findByEmail(email.trim()).map(User::getId);
        if (accountId.isEmpty()) {
            return;
        }

        // 이전에 보낸 링크는 무효로 하고 새 링크 하나만 살아 있게 한다.
        tokenRepository.deleteByAccountTypeAndAccountId(type, accountId.get());
        String rawToken = newRawToken();
        tokenRepository.save(new PasswordResetToken(hash(rawToken), type, accountId.get(), Instant.now().plus(TOKEN_TTL)));

        String link = frontendOrigin + "/reset-password?token=" + rawToken;
        boolean sent = mailService.send(email.trim(), "비밀번호 재설정 안내",
                "아래 링크에서 새 비밀번호를 설정해주세요. 링크는 30분 동안 한 번만 쓸 수 있어요.\n\n"
                        + link + "\n\n"
                        + "비밀번호 재설정을 요청하지 않으셨다면 이 메일을 무시해주세요.");
        if (!sent) {
            // 링크 자체가 곧 계정 열쇠라, 로그에 남기는 건 LOG_RESET_LINKS=true로 명시한 개발 환경에서만 한다.
            // 운영에서 메일 설정을 빠뜨려도 로그를 볼 수 있는 사람이 남의 계정 비밀번호를 바꿀 수 없게.
            if (logResetLinks) {
                log.warn("[개발용] 메일 미설정으로 비밀번호 재설정 링크를 로그에 남깁니다: {}", link);
            } else {
                log.error("메일 설정이 없어 비밀번호 재설정 메일을 보내지 못했습니다. MAIL_* 설정을 확인하세요.");
            }
        }
    }

    public void confirmReset(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken == null ? "" : rawToken))
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(() -> new IllegalArgumentException("링크가 만료됐거나 이미 사용됐어요. 비밀번호 찾기를 다시 요청해주세요."));
        if ("LAWYER".equals(token.getAccountType())) {
            lawyerAuthService.resetPassword(token.getAccountId(), newPassword);
        } else {
            authService.resetPassword(token.getAccountId(), newPassword);
        }
        token.markUsed();
        tokenRepository.save(token);
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
