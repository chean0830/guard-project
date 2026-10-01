package com.projectguard.backend.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 앱 소셜 로그인 (PKCE 방식). 앱이 verifier를 만들고 그 해시(challenge)만 웹 로그인에 실어 보낸다 →
 * 웹이 소셜 로그인을 끝내고 받은 세션 토큰을 challenge와 함께 여기 맡기고 1회용 코드를 받아 앱으로 돌려보낸다 →
 * 앱이 코드 + verifier로 세션 토큰을 찾아간다. 코드는 2분 안에 한 번만 쓸 수 있다.
 */
@Service
public class AppLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration CODE_TTL = Duration.ofMinutes(2);
    /** base64url, 패딩 없음: SHA-256 해시는 43자, verifier는 RFC 7636대로 43~128자. */
    private static final Pattern CHALLENGE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern VERIFIER = Pattern.compile("[A-Za-z0-9._~-]{43,128}");

    private final AppLoginCodeRepository repository;

    public AppLoginService(AppLoginCodeRepository repository) {
        this.repository = repository;
    }

    public record Exchanged(String token, String email) {
    }

    @Transactional
    public String issueCode(String sessionToken, String email, String codeChallenge) {
        if (codeChallenge == null || !CHALLENGE.matcher(codeChallenge).matches()) {
            throw new IllegalArgumentException("앱 로그인 요청이 올바르지 않습니다.");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new AppLoginCode(sha256Hex(code), codeChallenge, sessionToken, email, Instant.now().plus(CODE_TTL)));
        return code;
    }

    /** 코드는 맞든 틀리든 한 번 시도하면 지운다 — verifier를 여러 번 맞혀보는 것을 막는다 (실패해도 삭제는 롤백하지 않음). */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public Exchanged exchange(String code, String verifier) {
        if (code == null || code.isBlank() || verifier == null || !VERIFIER.matcher(verifier).matches()) {
            throw new InvalidCredentialsException("로그인 정보가 올바르지 않습니다. 다시 시도해주세요.");
        }
        AppLoginCode saved = repository.findById(sha256Hex(code))
                .orElseThrow(() -> new InvalidCredentialsException("로그인 정보가 만료되었습니다. 다시 시도해주세요."));
        repository.delete(saved);
        boolean challengeMatches = MessageDigest.isEqual(
                saved.getCodeChallenge().getBytes(StandardCharsets.US_ASCII),
                challengeOf(verifier).getBytes(StandardCharsets.US_ASCII));
        if (!challengeMatches || saved.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException("로그인 정보가 만료되었습니다. 다시 시도해주세요.");
        }
        return new Exchanged(saved.getSessionToken(), saved.getEmail());
    }

    static String challengeOf(String verifier) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
    }

    private static String sha256Hex(String raw) {
        return HexFormat.of().formatHex(sha256(raw));
    }

    private static byte[] sha256(String raw) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
