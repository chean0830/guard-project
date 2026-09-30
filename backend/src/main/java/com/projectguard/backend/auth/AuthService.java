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
    private static final String WRONG_CREDENTIALS = "이메일 또는 비밀번호가 올바르지 않습니다. ("
            + LoginLockedException.MAX_FAILED_ATTEMPTS + "회 틀리면 로그인이 잠겨요)";

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
                .orElseThrow(() -> new InvalidCredentialsException(WRONG_CREDENTIALS));
        if (user.isLoginLocked()) {
            throw new LoginLockedException();
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            boolean locked = user.recordLoginFailure(LoginLockedException.MAX_FAILED_ATTEMPTS);
            userRepository.save(user);
            if (locked) {
                throw new LoginLockedException();
            }
            throw new InvalidCredentialsException(WRONG_CREDENTIALS);
        }
        requireNotBlocked(user);
        if (user.getFailedLoginCount() > 0) {
            user.resetLoginFailures();
            userRepository.save(user);
        }
        return issueToken(user);
    }

    /**
     * 소셜 로그인 콜백을 실제로 검증하는 건 프론트엔드(Next.js) 쪽이다 — 여기서는 이미 검증된
     * (provider, providerId, email, emailVerified)를 받아 회원을 찾거나 새로 만들고 세션 토큰만 발급한다.
     *
     * 같은 이메일이면 한 계정으로 합치는데, 이메일 소유가 확인된(emailVerified) 경우에만 그렇게 한다.
     * 인증되지 않은 이메일을 믿고 합치면, 남의 이메일을 적어둔 소셜 계정으로 그 사람 계정에 들어갈 수
     * 있기 때문(계정 탈취). 이미 이 소셜 계정으로 만들어진 회원이면 이메일 인증 여부와 상관없이 로그인된다.
     */
    public AuthResult oauthLogin(String provider, String providerId, String email, boolean emailVerified) {
        Optional<User> linked = userRepository.findByProviderAndProviderId(provider, providerId);
        if (linked.isPresent()) {
            requireNotBlocked(linked.get());
            return issueToken(linked.get());
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일 제공에 동의해야 로그인할 수 있습니다.");
        }
        if (!emailVerified) {
            throw new IllegalArgumentException("이메일 인증이 확인되지 않은 계정이에요. 해당 서비스에서 이메일을 인증한 뒤 다시 시도하거나, 다른 방법으로 로그인해주세요.");
        }
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(new User(email, provider, providerId)));
        requireNotBlocked(user);
        return issueToken(user);
    }

    /** 비밀번호 찾기로 새 비밀번호를 정한다. 로그인 잠금을 풀고, 기존 세션은 모두 끊는다. */
    public void resetPassword(Long userId, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("새 비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        user.resetLoginFailures();
        userRepository.save(user);
        authTokenRepository.deleteByUserId(userId);
    }

    public Optional<User> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return authTokenRepository.findById(token)
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .flatMap(t -> userRepository.findById(t.getUserId()))
                // 정지 처리 전에 발급된 세션도 즉시 끊기도록, 토큰이 살아있어도 정지 계정은 인증 실패로 본다.
                .filter(u -> !u.isBlocked());
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            authTokenRepository.deleteById(token);
        }
    }

    public void updateProfile(Long userId, String name) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
        user.updateName(name);
        userRepository.save(user);
    }

    /** 소셜 로그인 계정(passwordHash가 없음)은 비밀번호가 없으므로 변경할 수 없다. */
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
        if (user.getPasswordHash() == null) {
            throw new IllegalArgumentException("소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("새 비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private void requireNotBlocked(User user) {
        if (user.isBlocked()) {
            throw new AccountBlockedException(user.getBlockedReason());
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
