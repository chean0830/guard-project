package com.projectguard.backend.auth;

import com.projectguard.backend.common.AccountMailService;
import com.projectguard.backend.common.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 회원가입 이메일 인증. 아무 이메일로나 가입해 무료 분석(아이디당 5회)을 무한히 받거나 남의 이메일을
 * 선점하는 것을 막는다. 흐름: 인증번호 요청 → 메일의 6자리 번호 확인 → 가입용 토큰을 받아 가입할 때 제출.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final int MAX_CODE_ATTEMPTS = 5;
    public static final int MAX_CODES_PER_DAY = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationRepository repository;
    private final UserRepository userRepository;
    private final AccountMailService mailService;
    private final RateLimitService rateLimitService;
    private final boolean logCodes;

    public EmailVerificationService(
            EmailVerificationRepository repository,
            UserRepository userRepository,
            AccountMailService mailService,
            RateLimitService rateLimitService,
            @Value("${LOG_VERIFICATION_CODES:false}") boolean logCodes
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.rateLimitService = rateLimitService;
        this.logCodes = logCodes;
    }

    public void requestCode(String rawEmail) {
        String email = normalize(rawEmail);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바른 이메일 주소를 입력해주세요.");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyExistsException("이미 가입된 이메일입니다. 로그인하거나 비밀번호 찾기를 이용해주세요.");
        }
        if (!rateLimitService.tryConsume("signup-code", email, MAX_CODES_PER_DAY)) {
            throw new IllegalStateException("인증번호는 하루 " + MAX_CODES_PER_DAY + "번까지 받을 수 있어요. 내일 다시 시도해주세요.");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        repository.deleteByEmail(email);
        repository.save(new EmailVerification(email, PasswordResetService.hash(email + ":" + code), Instant.now().plus(CODE_TTL)));

        boolean sent = mailService.send(email, "회원가입 인증번호",
                "Project Guard 회원가입 인증번호는 [" + code + "] 입니다.\n\n"
                        + "10분 안에 가입 화면에 입력해주세요. 본인이 요청하지 않았다면 이 메일을 무시해주세요.");
        if (!sent) {
            if (logCodes) {
                log.warn("[개발용] 메일 미설정으로 인증번호를 로그에 남깁니다: {} → {}", email, code);
            } else {
                log.error("메일 설정이 없어 회원가입 인증번호를 보내지 못했습니다. MAIL_* 설정을 확인하세요.");
            }
        }
    }

    /** 인증번호가 맞으면 가입할 때 제출할 1회용 토큰을 돌려준다. */
    public String confirmCode(String rawEmail, String code) {
        String email = normalize(rawEmail);
        EmailVerification verification = repository.findTopByEmailOrderByIdDesc(email)
                .filter(v -> v.getVerifiedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("먼저 인증번호를 받아주세요."));
        if (verification.getCodeExpiresAt().isBefore(Instant.now()) || verification.getFailedAttempts() >= MAX_CODE_ATTEMPTS) {
            throw new IllegalArgumentException("인증번호가 만료됐어요. 인증번호를 다시 받아주세요.");
        }
        if (code == null || !PasswordResetService.hash(email + ":" + code.trim()).equals(verification.getCodeHash())) {
            verification.recordFailure();
            repository.save(verification);
            int left = MAX_CODE_ATTEMPTS - verification.getFailedAttempts();
            throw new IllegalArgumentException(left > 0
                    ? "인증번호가 맞지 않아요. (남은 입력 " + left + "회)"
                    : "인증번호를 " + MAX_CODE_ATTEMPTS + "번 틀렸어요. 인증번호를 다시 받아주세요.");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        verification.markVerified(PasswordResetService.hash(token));
        repository.save(verification);
        return token;
    }

    /** 가입 직전에 호출한다. 이 이메일로 인증을 마친 30분 이내의 토큰이어야 하고, 한 번 쓰면 끝이다. */
    public void consumeToken(String rawEmail, String token) {
        String email = normalize(rawEmail);
        EmailVerification verification = repository.findByTokenHash(PasswordResetService.hash(token == null ? "" : token))
                .filter(v -> v.getEmail().equals(email))
                .filter(v -> v.getUsedAt() == null)
                .filter(v -> v.getVerifiedAt() != null && v.getVerifiedAt().plus(TOKEN_TTL).isAfter(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("이메일 인증을 먼저 완료해주세요. (인증 후 30분이 지났다면 다시 인증해주세요)"));
        verification.markUsed();
        repository.save(verification);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
