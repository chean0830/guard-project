package com.projectguard.backend.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, AuthTokenRepository authTokenRepository) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
    }

    public AuthResult signup(String email, String password) {
        validateCredentialFormat(email, password);
        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyExistsException("이미 가입된 이메일입니다.");
        }

        User user = userRepository.save(new User(email, passwordEncoder.encode(password)));
        return issueToken(user);
    }

    public AuthResult login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .filter(u -> u.getPasswordHash() != null && passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));
        return issueToken(user);
    }

    /**
     * 소셜 로그인 콜백을 실제로 검증하는 건 프론트엔드(Next.js) 쪽이다 — 여기서는 이미 검증된
     * (provider, providerId, email)을 받아 회원을 찾거나 새로 만들고 세션 토큰만 발급한다.
     * 같은 이메일로 이미 가입돼 있으면(로컬 가입이든 다른 소셜이든) 같은 계정으로 로그인시킨다.
     */
    public AuthResult oauthLogin(String provider, String providerId, String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일 제공에 동의해야 로그인할 수 있습니다.");
        }
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(new User(email, provider, providerId)));
        return issueToken(user);
    }

    public Optional<User> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return authTokenRepository.findById(token)
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .flatMap(t -> userRepository.findById(t.getUserId()));
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            authTokenRepository.deleteById(token);
        }
    }

    private AuthResult issueToken(User user) {
        String token = UUID.randomUUID().toString();
        authTokenRepository.save(new AuthToken(token, user.getId(), Instant.now().plus(TOKEN_TTL)));
        return new AuthResult(token, user.getEmail());
    }

    private void validateCredentialFormat(String email, String password) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바른 이메일 주소를 입력해주세요.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
    }
}
