package com.projectguard.backend.lawyer;

import com.projectguard.backend.auth.AccountBlockedException;
import com.projectguard.backend.auth.EmailAlreadyExistsException;
import com.projectguard.backend.auth.InvalidCredentialsException;
import com.projectguard.backend.auth.LoginLockedException;
import com.projectguard.backend.common.UploadFileTypes;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 변호사 회원가입은 일반 회원가입(auth.AuthService)과 달리 가입 즉시 로그인할 수 없다.
 * 자격 증명 서류를 제출하면 PENDING 상태로 저장되고, 관리자(LawyerAdminController)가 서류를
 * 검토해 승인해야 로그인이 열린다 — 아무나 "변호사입니다"라고 주장하며 가입해 변호사 상담
 * 기능을 악용하는 것을 막기 위함.
 */
@Service
public class LawyerAuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private final LawyerRepository lawyerRepository;
    private final LawyerCredentialDocumentRepository documentRepository;
    private final LawyerAuthTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LawyerAuthService(
            LawyerRepository lawyerRepository,
            LawyerCredentialDocumentRepository documentRepository,
            LawyerAuthTokenRepository tokenRepository
    ) {
        this.lawyerRepository = lawyerRepository;
        this.documentRepository = documentRepository;
        this.tokenRepository = tokenRepository;
    }

    public Lawyer signup(
            String email,
            String password,
            String name,
            String lawFirm,
            String barNumber,
            List<MultipartFile> documents
    ) {
        validateSignupFields(email, password, name, barNumber);
        List<MultipartFile> attachments = documents == null
                ? List.of()
                : documents.stream().filter(file -> !file.isEmpty()).toList();
        if (attachments.isEmpty()) {
            throw new IllegalArgumentException("변호사 자격을 확인할 수 있는 서류를 1개 이상 첨부해주세요.");
        }
        if (lawyerRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyExistsException("이미 가입 신청된 이메일입니다.");
        }
        // 관리자가 여는 파일이라, 실행 파일·HTML 등이 섞이지 않도록 실제 내용으로 형식을 확인한다.
        List<byte[]> contents = attachments.stream().map(this::readBytes).toList();
        List<String> detectedTypes = contents.stream()
                .map(bytes -> UploadFileTypes.detect(bytes).orElseThrow(() -> new IllegalArgumentException(
                        "자격 서류는 " + UploadFileTypes.ALLOWED_DESCRIPTION + " 파일만 올릴 수 있어요.")))
                .toList();

        Lawyer lawyer = lawyerRepository.save(
                new Lawyer(email, passwordEncoder.encode(password), name, lawFirm, barNumber)
        );
        for (int i = 0; i < attachments.size(); i++) {
            documentRepository.save(new LawyerCredentialDocument(
                    lawyer, attachments.get(i).getOriginalFilename(), detectedTypes.get(i), contents.get(i)
            ));
        }
        return lawyer;
    }

    public LawyerAuthResult login(String email, String password) {
        String wrong = "이메일 또는 비밀번호가 올바르지 않습니다. (" + LoginLockedException.MAX_FAILED_ATTEMPTS + "회 틀리면 로그인이 잠겨요)";
        Lawyer lawyer = lawyerRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException(wrong));
        if (lawyer.isLoginLocked()) {
            throw new LoginLockedException();
        }
        if (!passwordEncoder.matches(password, lawyer.getPasswordHash())) {
            boolean locked = lawyer.recordLoginFailure(LoginLockedException.MAX_FAILED_ATTEMPTS);
            lawyerRepository.save(lawyer);
            if (locked) {
                throw new LoginLockedException();
            }
            throw new InvalidCredentialsException(wrong);
        }
        if (lawyer.getFailedLoginCount() > 0) {
            lawyer.resetLoginFailures();
            lawyerRepository.save(lawyer);
        }

        switch (lawyer.getStatus()) {
            case PENDING -> throw new LawyerNotApprovedException(
                    "관리자 승인 대기 중입니다. 제출하신 서류 검토 후 로그인하실 수 있습니다.");
            case REJECTED -> throw new LawyerNotApprovedException(
                    "가입 승인이 거절되었습니다." + (lawyer.getRejectionReason() != null && !lawyer.getRejectionReason().isBlank()
                            ? " 사유: " + lawyer.getRejectionReason()
                            : ""));
            case APPROVED -> { }
        }
        if (lawyer.isBlocked()) {
            throw new AccountBlockedException(lawyer.getBlockedReason());
        }
        return issueToken(lawyer);
    }

    public Optional<Lawyer> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository.findById(token)
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .flatMap(t -> lawyerRepository.findById(t.getLawyerId()))
                .filter(l -> !l.isBlocked());
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            tokenRepository.deleteById(token);
        }
    }

    public Lawyer updateProfile(Long lawyerId, String name, String lawFirm, String specialties, String introduction) {
        Lawyer lawyer = requireLawyer(lawyerId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름을 입력해주세요.");
        }
        lawyer.updateProfile(name, lawFirm, specialties, introduction);
        return lawyerRepository.save(lawyer);
    }

    public Lawyer updateStrengths(Long lawyerId, String headline, Integer careerYears, String feeInfo, String achievements) {
        Lawyer lawyer = requireLawyer(lawyerId);
        requireMaxLength(headline, 100, "한 줄 강점");
        requireMaxLength(feeInfo, 300, "수임료 안내");
        requireMaxLength(achievements, 1000, "주요 실적");
        if (careerYears != null && (careerYears < 0 || careerYears > 70)) {
            throw new IllegalArgumentException("경력 연차를 올바르게 입력해주세요.");
        }
        lawyer.updateStrengths(blankToNull(headline), careerYears, blankToNull(feeInfo), blankToNull(achievements));
        return lawyerRepository.save(lawyer);
    }

    private void requireMaxLength(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(label + "은(는) " + max + "자 이내로 입력해주세요.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 비밀번호 찾기로 새 비밀번호를 정한다. 로그인 잠금을 풀고, 기존 세션은 모두 끊는다. */
    public void resetPassword(Long lawyerId, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("새 비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
        lawyer.changePasswordHash(passwordEncoder.encode(newPassword));
        lawyer.resetLoginFailures();
        lawyerRepository.save(lawyer);
        tokenRepository.deleteByLawyerId(lawyerId);
    }

    public void updateEmailNotificationsEnabled(Long lawyerId, boolean enabled) {
        Lawyer lawyer = requireLawyer(lawyerId);
        lawyer.setEmailNotificationsEnabled(enabled);
        lawyerRepository.save(lawyer);
    }

    public void changePassword(Long lawyerId, String currentPassword, String newPassword) {
        Lawyer lawyer = requireLawyer(lawyerId);
        if (!passwordEncoder.matches(currentPassword, lawyer.getPasswordHash())) {
            throw new InvalidCredentialsException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("새 비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
        lawyer.changePasswordHash(passwordEncoder.encode(newPassword));
        lawyerRepository.save(lawyer);
    }

    private Lawyer requireLawyer(Long lawyerId) {
        return lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
    }

    private LawyerAuthResult issueToken(Lawyer lawyer) {
        String token = UUID.randomUUID().toString();
        tokenRepository.save(new LawyerAuthToken(token, lawyer.getId(), Instant.now().plus(TOKEN_TTL)));
        return new LawyerAuthResult(token, lawyer.getEmail(), lawyer.getName());
    }

    private void validateSignupFields(String email, String password, String name, String barNumber) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바른 이메일 주소를 입력해주세요.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름을 입력해주세요.");
        }
        if (barNumber == null || barNumber.isBlank()) {
            throw new IllegalArgumentException("변호사 등록번호를 입력해주세요.");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("첨부 파일을 읽는 중 오류가 발생했습니다.", e);
        }
    }
}
